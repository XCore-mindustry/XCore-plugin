import contextlib
import io
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from types import SimpleNamespace
from unittest.mock import Mock
from devtools import configcmd as cli

cli.ROOT = Path(__file__).resolve().parents[2]


class ConfigTest(unittest.TestCase):
    def test_structural_diff_redacts_multiline_and_nested_credentials(self):
            old = cli.parse_toml(b'[database]\npassword="old"\n')
            new = cli.parse_toml(b'[database]\npassword="""new\nsecret"""\n')
            self.assertEqual([("changed", "database.password")], cli.changed_keys(old, new))

    def test_invalid_toml_never_touches_live_config(self):
            with tempfile.TemporaryDirectory() as directory:
                source = Path(directory) / "invalid.toml"
                source.write_text('token = "sensitive\n')
                compose = Mock()
                with self.assertRaises(cli.CommandError) as error:
                    cli.config_command(SimpleNamespace(operation="apply", name="xcore", source=source), compose)
                compose.config_file.assert_not_called()
                self.assertNotIn("sensitive", str(error.exception))

    def test_apply_restarts_only_mindustry_and_unchanged_does_not_restart(self):
            with tempfile.TemporaryDirectory() as directory:
                source = Path(directory) / "xcore.toml"
                source.write_text('[server]\nname="dev"\n')
                compose = Mock(game="mindustry-server")
                args = SimpleNamespace(operation="apply", name="xcore", source=source)
                with contextlib.redirect_stdout(io.StringIO()):
                    compose.config_file.return_value = "Backup: example\n"
                    cli.config_command(args, compose)
                    compose.compose.assert_called_once_with("restart", "mindustry-server", timeout=45)
                    compose.reset_mock()
                    compose.config_file.return_value = "Unchanged\n"
                    cli.config_command(args, compose)
                    compose.compose.assert_not_called()

    def test_atomic_install_backup_restore_and_rejected_validation(self):
            with tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                live = root / "xcore.toml"
                live.write_text('name="original"\n')
                (root / "saves").mkdir()
                save = root / "saves/world.msav"
                save.write_bytes(b"world")
                # Isolate the file installation contract from the Java parser, tested with actual JARs separately.
                java = root / "java"
                java.write_text('#!/bin/sh\ncase "$(cat "$5")" in *reject*) exit 1;; esac\n')
                java.chmod(0o700)
                environment = dict(os.environ, DEV_CONFIG_DIR=str(root), PATH=f"{root}:{os.environ['PATH']}")
                script = cli.ROOT / "devtools/mindustry/tools/config-file.sh"

                def run(action, data=None, backup=None):
                    return subprocess.run(["bash", str(script), action, "xcore", *([backup] if backup else [])],
                                          input=data, capture_output=True, env=environment)

                applied = run("apply", b'name="updated"\n')
                self.assertEqual(0, applied.returncode, applied.stderr.decode())
                backup = applied.stdout.decode().strip().removeprefix("Backup: ")
                saved = root / ".config-backups/xcore" / backup
                self.assertEqual('name="original"\n', saved.read_text())
                self.assertEqual(0o600, saved.stat().st_mode & 0o777)
                self.assertEqual(0o600, live.stat().st_mode & 0o777)
                self.assertEqual(0, run("apply", live.read_bytes()).returncode)
                self.assertEqual(1, len(list(saved.parent.iterdir())))
                rejected = run("apply", b'name="reject"\n')
                self.assertNotEqual(0, rejected.returncode)
                self.assertEqual('name="updated"\n', live.read_text())
                self.assertEqual(0, run("restore", backup=backup).returncode)
                self.assertEqual('name="original"\n', live.read_text())
                self.assertEqual(b"world", save.read_bytes())
                self.assertNotEqual(0, run("restore", backup="../../xcore.toml").returncode)
                self.assertFalse(list(root.glob(".xcore.*.toml")))
