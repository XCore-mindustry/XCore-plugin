import contextlib
import hashlib
import io
import json
import subprocess
import tempfile
import tomllib
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import Mock, patch
from zipfile import ZipFile

from devtools import __main__ as app, plugins, settings
from devtools.runtime import Compose, prepare, stage
from devtools.targets import Plugin, Services, TARGETS

ROOT = Path(__file__).resolve().parents[2]


class DevelopmentTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.path = self.root / "local.toml"
        self.settings = settings.load(self.path)

    def jar(self, name, deps=(), soft=(), mod=None):
        path = self.root / f"{name}.jar"
        with ZipFile(path, "w") as out:
            out.writestr("plugin.json", json.dumps({"name": mod or name, "dependencies": list(deps), "softDependencies": list(soft)}))
        return path

    def target(self, name, deps=(), soft=(), mod=None):
        jar = self.jar(name, deps, soft, mod)
        return Plugin(mod or name, ":" + name, jar.name)

    def resolve(self, names, registry):
        with patch.object(plugins, "TARGETS", registry):
            return plugins.resolve(names, self.settings, self.root, run=Mock())

    def test_hard_closure_and_mindustry_normalization(self):
        registry = {"addon": self.target("addon", ["Core Mod"]), "core": self.target("core", mod="CORE MOD")}
        jars, hashes, services, game, names = self.resolve(["addon"], registry)
        self.assertEqual(["core.jar", "addon.jar"], [p.name for p in jars])
        self.assertEqual({"game"}, services)
        self.assertEqual({"core-mod", "addon"}, set(hashes))
        self.assertEqual(["core", "addon"], names)

    def test_root_build_tasks_are_batched(self):
        registry = {"a": self.target("a"), "b": self.target("b")}
        run = Mock()
        with patch.object(plugins, "TARGETS", registry):
            plugins.resolve(["a", "b"], self.settings, self.root, run)
        self.assertEqual(1, run.call_count)
        self.assertEqual([str(self.root / "gradlew"), ":a", ":b", "--console=plain"], run.call_args.args[0])

    def test_soft_dependency_is_not_built_but_orders_selected_plugins(self):
        registry = {"a": self.target("a", soft=["b"]), "b": self.target("b")}
        self.assertEqual(["a.jar"], [p.name for p in self.resolve(["a"], registry)[0]])
        self.assertEqual(["b.jar", "a.jar"], [p.name for p in self.resolve(["a", "b"], registry)[0]])

    def test_missing_ambiguous_cycle_and_invalid_jar_fail(self):
        cases = [
            {"a": self.target("a", ["absent"])},
            {"a": self.target("a", ["b"]), "b": self.target("b", mod="b"), "other": self.target("other", mod="b")},
            {"a": self.target("a", ["b"]), "b": self.target("b", ["a"])},
        ]
        for registry in cases:
            with self.subTest(registry=list(registry)), self.assertRaises(settings.CommandError):
                self.resolve(["a"], registry)
        jar = self.root / "bad.jar"
        with ZipFile(jar, "w") as out:
            out.writestr("other.json", "{}")
        with self.assertRaises(settings.CommandError):
            plugins.manifest(jar)

    def test_satellite_mod_alias_and_source_build(self):
        self.jar("a", ["custom-mod"])
        sat = self.jar("external", mod="Custom Mod")
        self.settings["satellite"] = {"custom": {"mod": "custom-mod", "jar": sat, "repo": self.root / "source", "task": "shadowJar"}}
        run = Mock()
        with patch.object(plugins, "TARGETS", {"a": Plugin("a", ":a", "a.jar")}):
            jars = plugins.resolve(["a"], self.settings, self.root, run)[0]
        self.assertEqual([sat, self.root / "a.jar"], jars)
        self.assertIn(([str(self.root / "source/gradlew"), "-p", str(self.root / "source"), "shadowJar", "--console=plain"], self.root / "source"), [c.args for c in run.call_args_list])

    def test_conflicting_satellite_game_settings(self):
        self.settings["satellite"] = {name: {"jar": self.jar(name), "game": {"auto_start": value}} for name, value in [("a", True), ("b", False)]}
        with self.assertRaises(settings.CommandError):
            self.resolve(["a", "b"], {})

    def test_config_rejects_unknown_keys_missing_tasks_and_wrong_types(self):
        for text in ['unknown="hidden"', '[game]\nauto_start="hidden"', '[satellite.a]\nrepo="source"\njar="a.jar"', '[satellite.a]\ntask="build"\njar="a.jar"', '[bot]\nguild_id=true', '[bot]\nguild_id=1']:
            self.path.write_text(text)
            with self.subTest(text=text), self.assertRaises(settings.CommandError) as error:
                settings.load(self.path)
            self.assertNotIn("hidden", str(error.exception))
        self.path.write_text('secret="never-print-this')
        with self.assertRaises(settings.CommandError) as error:
            settings.load(self.path)
        self.assertNotIn("never-print-this", str(error.exception))

    def test_relative_satellite_paths_and_local_file_not_rewritten(self):
        content = '[satellite.a]\nrepo="source"\ntask="shadowJar"\njar="build/a.jar"\n'
        self.path.write_text(content)
        item = settings.load(self.path)["satellite"]["a"]
        self.assertEqual(self.root / "source/build/a.jar", item["jar"])
        self.assertEqual(content, self.path.read_text())

    def test_permission_seed_omits_disabled_discord_and_quotes_configured_guild(self):
        run = self.root / ".run"
        prepare(run, self.settings, write_permission=True)
        seed = run / "permissions.toml"
        self.assertEqual({"schemaVersion": 1}, tomllib.loads(seed.read_text()))
        self.settings["bot"]["guild_id"] = 123456789012345678
        prepare(run, self.settings, write_permission=True)
        self.assertEqual({"schemaVersion": 1, "discord": {"guildId": "123456789012345678"}}, tomllib.loads(seed.read_text()))
        self.assertEqual(0o600, seed.stat().st_mode & 0o777)

    def test_staging_replaces_previous_set_and_restores_on_publish_error(self):
        run = self.root / ".run"
        prepare(run, self.settings)
        stage(run, [self.jar("a"), self.jar("b")])
        stage(run, [self.jar("c")])
        self.assertEqual(["000.jar"], sorted(p.name for p in (run / "mods").iterdir()))
        original = Path.rename
        def rename(path, target):
            if path.name == "mods.new":
                raise OSError("test failure")
            return original(path, target)
        with patch.object(Path, "rename", rename), self.assertRaises(OSError):
            stage(run, [self.jar("d")])
        self.assertEqual("c", plugins.manifest(run / "mods/000.jar")["name"])

    def fake_compose(self, running):
        fake = Mock()
        live = set(running)
        fake.closure.side_effect = lambda names: set(names) | {"mongo", "redis"}
        def compose(*args, **kwargs):
            if args[:1] == ("ps",):
                return "\n".join(sorted(live))
            if args[:1] == ("up",):
                live.update({x for x in args[1:] if not x.startswith("-")})
            if args[:1] == ("stop",):
                live.difference_update(args[1:])
            return ""
        fake.compose.side_effect = compose
        return fake

    def test_up_replaces_targets_and_stops_obsolete_services_only(self):
        run = self.root / ".run"
        prepare(run, self.settings)
        stage(run, [self.jar("old")])
        fake = self.fake_compose({"game", "mongo", "redis"})
        result = ([], {}, {"web", "web-seed"}, self.settings["game"], [])
        with patch.object(app, "ROOT", self.root), patch.object(app, "resolve", return_value=result), contextlib.redirect_stdout(io.StringIO()):
            app.change(SimpleNamespace(command="up", targets=["web"]), self.settings, fake, run, {"hashes": {"old": "old"}})
        self.assertIn(unittest.mock.call("stop", "game"), fake.compose.call_args_list)
        self.assertEqual([], list((run / "mods").iterdir()))
        self.assertEqual(["web"], json.loads((run / "targets.json").read_text())["targets"])

    def test_reload_unchanged_does_not_restart_and_failure_does_not_touch_runtime(self):
        run = self.root / ".run"
        prepare(run, self.settings)
        jar = self.jar("a")
        stage(run, [jar])
        fingerprint = hashlib.sha256(json.dumps(self.settings["game"], sort_keys=True).encode()).hexdigest()
        old = {"targets": ["a"], "hashes": {"a": "digest"}, "game": fingerprint}
        fake = self.fake_compose({"game"})
        with patch.object(app, "resolve", return_value=([jar], old["hashes"], {"game"}, self.settings["game"], ["a"])), contextlib.redirect_stdout(io.StringIO()):
            app.change(SimpleNamespace(command="reload"), self.settings, fake, run, old)
        fake.compose.assert_not_called()
        with patch.object(app, "resolve", side_effect=settings.CommandError("build failed")), self.assertRaises(settings.CommandError):
            app.change(SimpleNamespace(command="reload"), self.settings, fake, run, old)
        self.assertEqual("a", plugins.manifest(run / "mods/000.jar")["name"])
        fake.compose.assert_not_called()

    def test_reload_recreates_game_when_environment_settings_change(self):
        run = self.root / ".run"
        prepare(run, self.settings)
        jar = self.jar("a")
        fake = self.fake_compose({"game", "mongo", "redis"})
        game = self.settings["game"] | {"server_name": "other", "auto_start": False}
        old = {"targets": ["a"], "hashes": {"a": "digest"}, "game": "old-settings"}
        with patch.object(app, "resolve", return_value=([jar], old["hashes"], {"game"}, game, ["a"])), contextlib.redirect_stdout(io.StringIO()):
            app.change(SimpleNamespace(command="reload"), self.settings, fake, run, old)
        self.assertIn(unittest.mock.call("up", "-d", "--build", "game"), fake.compose.call_args_list)
        self.assertEqual("other", fake.env["MINDUSTRY_SERVER_NAME"])
        self.assertEqual("false", fake.env["XCORE_AUTO_START"])

    def test_every_compose_invocation_has_all_secret_env_but_errors_hide_values(self):
        self.settings["web"]["discord_client_secret"] = "secret-never-logged"
        compose = Compose(self.root, self.settings, self.settings["game"])
        with patch("devtools.runtime.subprocess.run", return_value=SimpleNamespace(returncode=0, stdout=b"")) as run:
            compose.compose("ps")
            compose.compose("restart", "game")
        self.assertTrue(all(c.kwargs["env"]["DISCORD_CLIENT_SECRET"] == "secret-never-logged" for c in run.call_args_list))
        with patch("devtools.runtime.subprocess.run", return_value=SimpleNamespace(returncode=1, stderr=b"secret-never-logged")), self.assertRaises(settings.CommandError) as error:
            compose.compose("up", "web")
        self.assertNotIn("secret-never-logged", str(error.exception))

    def test_down_keeps_volumes_and_reset_needs_confirmation(self):
        fake = self.fake_compose(set())
        with patch.object(app, "ROOT", self.root), patch.object(app, "Compose", return_value=fake), contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual(0, app.main(["down"]))
            fake.compose.assert_called_once_with("down")
            fake.compose.reset_mock()
            with patch("builtins.input", return_value="no"):
                self.assertEqual(1, app.main(["reset"]))
            fake.compose.assert_not_called()
            with patch("builtins.input", return_value="RESET"):
                self.assertEqual(0, app.main(["reset"]))
            fake.compose.assert_called_once_with("down", "--volumes")

    def test_registry_matches_real_compose_services(self):
        data = json.loads(subprocess.check_output(["docker", "compose", "-f", str(ROOT / "devtools/compose.yaml"), "config", "--format", "json"]))
        registered = {"game"}
        for target in TARGETS.values():
            if isinstance(target, Services):
                registered.update(target.names)
        self.assertFalse(registered - data["services"].keys())
        for service in data["services"].values():
            self.assertNotIn("container_name", service)
            self.assertNotIn("profiles", service)
            for port in service.get("ports", []):
                self.assertEqual("127.0.0.1", port["host_ip"])


if __name__ == "__main__":
    unittest.main()
