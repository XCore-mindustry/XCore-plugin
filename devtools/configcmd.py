import tomllib

from .settings import CommandError


def parse_toml(data):
    try:
        return tomllib.loads(data.decode())
    except (UnicodeDecodeError, tomllib.TOMLDecodeError) as exc:
        raise CommandError("Invalid TOML; check syntax and encoding. No files were changed.") from exc


def changed_keys(old, new, prefix=""):
    """A useful structural diff which never emits config values (including multiline secrets)."""
    changes = []
    for key in sorted(old.keys() | new.keys()):
        path = f"{prefix}.{key}" if prefix else key
        if key not in old:
            changes.append(("added", path))
        elif key not in new:
            changes.append(("removed", path))
        elif isinstance(old[key], dict) and isinstance(new[key], dict):
            changes.extend(changed_keys(old[key], new[key], path))
        elif type(old[key]) is not type(new[key]) or old[key] != new[key]:
            changes.append(("changed", path))
    return changes


def config_command(args, compose):
    if args.operation == "restore":
        if not args.backup:
            raise CommandError("restore requires a backup ID printed by apply.")
        receipt = compose.config_file("restore", args.name, backup=args.backup)
    else:
        source = args.source or compose.config_dir / f"{args.name}.toml"
        candidate = source.read_bytes()
        proposed = parse_toml(candidate)
        if args.operation == "diff":
            current = parse_toml(compose.config_file("read", args.name).encode())
            changes = changed_keys(current, proposed)
            print(f"Live {args.name}.toml -> {source} (values hidden)")
            for status, path in changes:
                print(f"  {status}: {path}")
            if not changes:
                print("No setting changes.")
            return 0
        receipt = compose.config_file("apply", args.name, data=candidate)
    print(receipt.strip())
    if receipt.strip() != "Unchanged":
        compose.compose("restart", compose.game, timeout=45)
        print("Config installed. Only Mindustry was restarted; databases and saves are preserved.")
        print("Startup gamemode/server-name overrides still apply to xcore.toml.")
    return 0
