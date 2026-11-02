import argparse
import hashlib
import json
import shutil
import subprocess
import sys
from pathlib import Path

from .configcmd import config_command
from .diagnostics import diagnostics
from .plugins import resolve
from .runtime import Compose, prepare, read_state, stage, write_state
from .settings import CommandError, effective_game, environment, load
from .targets import TARGETS

ROOT = Path(__file__).resolve().parent.parent


def change(args, settings, compose, run, old):
    names = args.targets if args.command == "up" else old["targets"] if old else None
    if not names:
        raise CommandError("No active targets. Run ./dev up first.")
    print("Building selected plugins...", flush=True)
    jars, hashes, services, game, plugins = resolve(names, settings, ROOT)
    compose.env = environment(settings, game)
    desired = compose.closure(services)
    prepare(run, settings, write_permission=True)
    fingerprint = hashlib.sha256(json.dumps(game, sort_keys=True).encode()).hexdigest()
    changed = not old or hashes != old["hashes"] or fingerprint != old.get("game")
    if args.command == "reload" and not changed and services == {"game"}:
        print("Nothing changed.")
        return 0
    running = set(compose.compose("ps", "--services", "--status", "running").split())
    if changed:
        stage(run, jars)
    if args.command == "up":
        print("Starting selected services...", flush=True)
        compose.compose("up", "-d", "--build", *sorted(services))
    else:
        applications = services - {"game"}
        if "game" in services and (fingerprint != old.get("game") or "game" not in running):
            applications.add("game")
        if applications:
            compose.compose("up", "-d", "--build", *sorted(applications))
    if changed and "game" in services and "game" in running:
        compose.compose("restart", "game", timeout=60)
    obsolete = set(compose.compose("ps", "--services", "--status", "running").split()) - desired
    if obsolete:
        compose.compose("stop", *sorted(obsolete))
    write_state(run / "targets.json", {"targets": list(names), "plugins": plugins,
                "services": sorted(services), "hashes": hashes, "game": fingerprint})
    shutil.rmtree(run / "mods.previous", ignore_errors=True)
    print("Started " + ", ".join(names) + ".")
    return 0


def parser():
    result = argparse.ArgumentParser(description="Run selected XCore development targets.")
    commands = result.add_subparsers(dest="command", required=True)
    up = commands.add_parser("up", help="Replace the active target set")
    up.add_argument("targets", nargs="+", help="Built-ins: " + ", ".join(TARGETS))
    for command in ("reload", "down", "reset", "status", "console"):
        commands.add_parser(command)
    logs = commands.add_parser("logs")
    logs.add_argument("service", nargs="?")
    check = commands.add_parser("check")
    check.add_argument("--json", action="store_true")
    check.add_argument("--timeout", type=float, default=5)
    config = commands.add_parser("config")
    config.add_argument("operation", choices=("diff", "apply", "restore"))
    config.add_argument("name", choices=("xcore", "sentinel", "secrets"))
    config.add_argument("backup", nargs="?")
    config.add_argument("--source", type=Path)
    return result


def main(argv=None):
    cli = parser()
    args = cli.parse_args(argv)
    if args.command == "check" and not 0 < args.timeout <= 60:
        cli.error("timeout must be between 0 and 60 seconds")
    if args.command == "config" and ((args.operation != "restore" and args.backup) or (args.operation == "restore" and args.source)):
        cli.error("backup is only for restore; --source is only for diff/apply")
    run = ROOT / "devtools/.run"
    locked = False
    try:
        settings = load(ROOT / "devtools/local.toml")
        old = read_state(run / "targets.json")
        game = effective_game(settings, old["plugins"] if old else [])
        prepare(run, settings)
        compose = Compose(ROOT, settings, game)
        if args.command in ("up", "reload", "down", "reset", "config"):
            try:
                (run / "lock").mkdir()
            except FileExistsError:
                raise CommandError("Another dev command is running (devtools/.run/lock).") from None
            locked = True
        if args.command in ("up", "reload"):
            return change(args, settings, compose, run, old)
        if args.command in ("down", "reset"):
            if args.command == "reset" and input("Type RESET to delete this dev stack's volumes: ") != "RESET":
                raise CommandError("Reset cancelled.")
            compose.compose("down", *(["--volumes"] if args.command == "reset" else []))
            (run / "targets.json").unlink(missing_ok=True)
            return 0
        if args.command == "status":
            print(compose.compose("ps"), end="")
            return 0
        if args.command == "check":
            args.expected = old["services"] if old else None
            return diagnostics(args, compose)
        if args.command == "config":
            if not old or "game" not in old["services"]:
                raise CommandError("Start a game target before editing its live config.")
            return config_command(args, compose)
        if args.command == "console":
            command = compose.prefix + ["attach", "game"]
        else:
            if args.service and args.service not in compose.closure(old["services"] if old else []):
                raise CommandError("Log service is not in the active stack.")
            command = compose.prefix + ["logs", "-f", *([args.service] if args.service else [])]
        return subprocess.run(command, env=compose.env).returncode
    except (CommandError, OSError, EOFError, ValueError, KeyError):
        # Parser, Docker and config exceptions may contain secrets; expose only controlled messages.
        error = sys.exception()
        print(f"Error: {error if isinstance(error, CommandError) else 'Command failed (details hidden).'}", file=sys.stderr)
        return 1
    finally:
        if locked:
            (run / "lock").rmdir()


if __name__ == "__main__":
    raise SystemExit(main())
