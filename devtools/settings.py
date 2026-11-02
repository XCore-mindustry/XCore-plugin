import os
import tomllib
from pathlib import Path

from .targets import TARGETS


class CommandError(Exception):
    pass


DEFAULTS = {
    "game": {"server_name": "dev", "gamemode": "survival", "auto_start": True, "jvm_opts": "-Xmx2G"},
    "web": {"origin": "http://localhost:3100", "discord_client_id": "", "discord_client_secret": ""},
    "bot": {"discord_bot_token": "", "guild_id": 0},
}


def check_table(value, allowed, label):
    if not isinstance(value, dict) or set(value) - set(allowed):
        raise CommandError(f"Unknown keys or invalid table in {label}.")


def game_values(values):
    check_table(values, DEFAULTS["game"], "game")
    for key, value in values.items():
        if type(value) is not type(DEFAULTS["game"][key]):
            raise CommandError(f"Invalid type for game.{key}.")
    if "gamemode" in values and values["gamemode"] not in ("survival", "sandbox", "attack", "pvp", "editor"):
        raise CommandError("Invalid game.gamemode.")
    if "server_name" in values and not values["server_name"].strip():
        raise CommandError("game.server_name must not be empty.")


def load(path):
    try:
        raw = tomllib.loads(path.read_text()) if path.exists() else {}
    except (OSError, UnicodeError, tomllib.TOMLDecodeError):
        raise CommandError("Cannot read local.toml; check its syntax (values hidden).") from None
    check_table(raw, (*DEFAULTS, "satellite"), "local.toml")
    result = {}
    for section, defaults in DEFAULTS.items():
        values = raw.get(section, {})
        check_table(values, defaults, section)
        for key, value in values.items():
            if type(value) is not type(defaults[key]):
                raise CommandError(f"Invalid type for {section}.{key}.")
        result[section] = defaults | values
    game_values(result["game"])
    guild = result["bot"]["guild_id"]
    if guild != 0 and not 10_000 <= guild < 10**25:
        raise CommandError("bot.guild_id must be zero or a Discord id of 5 to 25 digits.")
    if result["bot"]["discord_bot_token"] and not result["bot"]["guild_id"]:
        raise CommandError("bot.guild_id is required with a bot token.")
    satellites = raw.get("satellite", {})
    if not isinstance(satellites, dict):
        raise CommandError("satellite must be a table.")
    result["satellite"] = {}
    for name, values in satellites.items():
        if name in TARGETS:
            raise CommandError("A satellite name conflicts with a built-in target.")
        check_table(values, ("repo", "task", "jar", "mod", "game"), "satellite")
        if not values.get("jar") or ("repo" in values) != ("task" in values):
            raise CommandError("A satellite needs jar; a source satellite also needs repo and task.")
        if any(not isinstance(v, str) or not v.strip() for k, v in values.items() if k != "game"):
            raise CommandError("Satellite paths, mod and task must be nonempty strings.")
        game_values(values.get("game", {}))
        item = dict(values)
        if "repo" in item:
            item["repo"] = resolve(path.parent, item["repo"])
        item["jar"] = resolve(item.get("repo", path.parent), item["jar"])
        result["satellite"][name] = item
    return result


def resolve(base, value):
    path = Path(value).expanduser()
    return (base / path).resolve() if not path.is_absolute() else path.resolve()


def environment(settings, game):
    return os.environ | {
        "MINDUSTRY_SERVER_NAME": game["server_name"], "MINDUSTRY_GAMEMODE": game["gamemode"],
        "XCORE_AUTO_START": str(game["auto_start"]).lower(), "JAVA_TOOL_OPTIONS": game["jvm_opts"] + " --enable-native-access=ALL-UNNAMED",
        "WEB_ORIGIN": settings["web"]["origin"], "DISCORD_CLIENT_ID": settings["web"]["discord_client_id"],
        "DISCORD_CLIENT_SECRET": settings["web"]["discord_client_secret"],
        "DISCORD_BOT_TOKEN": settings["bot"]["discord_bot_token"], "DISCORD_GUILD_ID": str(settings["bot"]["guild_id"]),
        "XCORE_ACCOUNT_SERVERS": game["server_name"], "PERMISSIONS_RPC_SERVER": game["server_name"],
    }


def effective_game(settings, names):
    overrides = {}
    for name in names:
        for key, value in settings["satellite"].get(name, {}).get("game", {}).items():
            if key in overrides and overrides[key] != value:
                raise CommandError("Selected satellites have conflicting game settings.")
            overrides[key] = value
    return settings["game"] | overrides
