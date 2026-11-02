import hashlib
import json
import os
import shutil
import subprocess
from pathlib import Path

from .settings import CommandError, environment


class Compose:
    def __init__(self, root, settings, game):
        self.root = root
        self.directory = root / "devtools"
        self.config_dir = self.directory / "mindustry/config"
        self.game = "game"
        project = "xcore-dev-" + hashlib.sha256(str(root).encode()).hexdigest()[:8]
        self.prefix = ["docker", "compose", "-p", project, "-f", str(self.directory / "compose.yaml")]
        self.env = environment(settings, game)

    def run(self, args, *, data=None, timeout=1200):
        try:
            result = subprocess.run(args, input=data, capture_output=True, timeout=timeout, env=self.env)
        except (OSError, subprocess.TimeoutExpired):
            raise CommandError("Command unavailable or timed out (details hidden).") from None
        if result.returncode:
            raise CommandError("Docker command failed; inspect service logs or Docker access (details hidden).")
        return result.stdout.decode()

    def compose(self, *args, **kwargs):
        return self.run(self.prefix + list(args), **kwargs)

    def config_file(self, action, name, *, data=None, backup=None):
        args = ["run", "--rm", "-T", "--no-deps", "--entrypoint", "bash", "game",
                "/dev-tools/config-file.sh", action, name]
        if backup:
            args.append(backup)
        return self.compose(*args, data=data, timeout=60)

    def closure(self, roots):
        services = json.loads(self.compose("config", "--format", "json"))["services"]
        selected, pending = set(roots), list(roots)
        while pending:
            name = pending.pop()
            if name not in services:
                raise CommandError("A target refers to an undefined compose service.")
            for dependency in services[name].get("depends_on", {}):
                if dependency not in selected:
                    selected.add(dependency)
                    pending.append(dependency)
        return selected


def read_state(path):
    if not path.exists():
        return None
    try:
        state = json.loads(path.read_text())
        for key in ("targets", "plugins", "services"):
            if not isinstance(state[key], list) or any(not isinstance(x, str) for x in state[key]):
                raise ValueError()
        if not isinstance(state["hashes"], dict):
            raise ValueError()
        return state
    except (OSError, KeyError, ValueError, TypeError):
        raise CommandError("Invalid saved target state. Start a new set with up.") from None


def write_state(path, state):
    temporary = path.with_suffix(".new")
    temporary.write_text(json.dumps(state, sort_keys=True))
    temporary.chmod(0o600)
    temporary.replace(path)


def stage(run, jars):
    candidate, current, previous = (run / n for n in ("mods.new", "mods", "mods.previous"))
    shutil.rmtree(candidate, ignore_errors=True)
    candidate.mkdir()
    for index, jar in enumerate(jars):
        shutil.copyfile(jar, candidate / f"{index:03d}.jar")
    # Publish only complete validated sets; the running server uses its own copied jars.
    shutil.rmtree(previous, ignore_errors=True)
    if current.exists():
        current.rename(previous)
    try:
        candidate.rename(current)
    except OSError:
        if previous.exists():
            previous.rename(current)
        raise


def prepare(run, settings, write_permission=False):
    run.mkdir(parents=True, exist_ok=True)
    (run / "mods").mkdir(exist_ok=True)
    if not write_permission:
        return
    permission = run / "permissions.toml"
    content = "schemaVersion = 1\n"
    if guild := settings["bot"]["guild_id"]:
        content += f'[discord]\nguildId = "{guild}"\n'
    permission.write_text(content)
    permission.chmod(0o600)
