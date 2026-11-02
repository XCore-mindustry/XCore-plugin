import contextlib
import io
import json
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch
import subprocess

from devtools import diagnostics as checks


class FakeCompose:
    def __init__(self, containers):
        self.containers = containers
        self.calls = []
    def compose(self, *args, timeout):
        assert timeout == 2
        if "config" in args:
            return json.dumps({"services": {"mongo": {}, "redis": {}, "mongo-init": {}, "web-seed": {}, "web": {"depends_on": {"mongo": {}, "mongo-init": {}, "web-seed": {}, "redis": {}}}}})
        return " ".join(c["Id"] for c in self.containers)
    def run(self, args, timeout):
        assert timeout == 2
        self.calls.append(args)
        if args[1] == "inspect":
            return json.dumps(self.containers)
        return "PONG" if "redis-cli" in args else "OK"


def container(name, running=True, ports=None):
    return {"Id": name, "Config": {"Labels": {"com.docker.compose.service": name}},
            "State": {"Running": running, "Status": "running" if running else "exited", "ExitCode": 0},
            "NetworkSettings": {"Ports": ports or {}}}


class DiagnosticsTest(unittest.TestCase):
    def test_running_only_and_required_missing_service(self):
        with contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(0, checks.diagnostics(SimpleNamespace(expected=None, json=True, timeout=2), FakeCompose([container("redis")])))
            self.assertEqual(1, checks.diagnostics(SimpleNamespace(expected=["web"], json=True, timeout=2), FakeCompose([container("redis")])))

    def test_web_checks_completed_seeds_transactions_and_published_port(self):
        fake = FakeCompose([container("redis"), container("mongo"), container("mongo-init", False), container("web-seed", False), container("web", ports={"3000/tcp": [{"HostIp": "127.0.0.1", "HostPort": "3100"}]})])
        with contextlib.redirect_stdout(io.StringIO()), patch.object(checks.urllib.request, "build_opener") as opener:
            opener.return_value.open.return_value.__enter__.return_value.status = 200
            self.assertEqual(0, checks.diagnostics(SimpleNamespace(expected=["web"], json=True, timeout=2), fake))
        opener.return_value.open.assert_called_once_with("http://127.0.0.1:3100/en/me", timeout=2)
        probe = next(args[-1] for args in fake.calls if "startTransaction" in args[-1])
        self.assertIn("abortTransaction()", probe)
        self.assertNotIn("commitTransaction", probe)

    def test_entrypoint_copies_only_staged_jars_and_preserves_live_config(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for name in ("config/mods", "config-seed", "staged"):
                (root / name).mkdir(parents=True)
            (root / "config/mods/old.jar").write_text("old")
            (root / "outside.jar").write_text("old")
            (root / "config/mods/link.jar").symlink_to(root / "outside.jar")
            (root / "staged/000.jar").write_text("new")
            (root / "config/xcore.toml").write_text('password="sensitive-live-value"')
            (root / "config-seed/xcore.toml").write_text('password="different"')
            script = (Path(__file__).parents[1] / "mindustry/entrypoint.sh").read_text()
            script = script.replace('/server/config-seed', str(root / 'config-seed')).replace('/server/config', str(root / 'config')).replace('/staged-mods', str(root / 'staged'))
            result = subprocess.run(["bash", "-c", script, "entrypoint", "true"], capture_output=True)
            self.assertEqual(0, result.returncode, result.stderr.decode())
            self.assertEqual(["000.jar"], sorted(p.name for p in (root / "config/mods").iterdir()))
            self.assertEqual('password="sensitive-live-value"', (root / "config/xcore.toml").read_text())
            self.assertNotIn(b"sensitive-live-value", result.stderr)
