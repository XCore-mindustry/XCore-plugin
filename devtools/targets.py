from dataclasses import dataclass


@dataclass(frozen=True)
class Plugin:
    mod: str
    task: str
    jar: str


@dataclass(frozen=True)
class Services:
    names: tuple[str, ...]


TARGETS = {
    "xcore": Plugin("xcore-plugin", ":plugins:xcore:devJar", "plugins/xcore/build/dev/XCore-plugin.jar"),
    "tilelogger": Plugin("TileLogger", ":plugins:tilelogger:jarRelease", "plugins/tilelogger/build/libs/TileLogger-release.jar"),
    "web": Services(("web", "web-seed")),
    "bot": Services(("discord-bot",)),
    "metrics": Services(("metrics-gateway", "prometheus", "grafana")),
}
