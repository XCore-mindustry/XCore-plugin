import time
from dataclasses import dataclass
from threading import Lock


@dataclass(frozen=True, slots=True)
class ServerInfo:
    name: str
    channel_id: int
    players: int
    max_players: int
    version: str
    host: str | None
    port: int | None
    last_seen_ts: float


class LiveServerRegistry:
    def __init__(self, timeout_sec: int = 90):
        self._servers: dict[str, ServerInfo] = {}
        self._lock = Lock()
        self._timeout = timeout_sec

    def update_server(
        self,
        name: str,
        channel_id: int,
        players: int,
        max_players: int,
        version: str,
        host: str | None = None,
        port: int | None = None,
    ) -> None:
        with self._lock:
            self._servers[name] = ServerInfo(
                name=name,
                channel_id=channel_id,
                players=players,
                max_players=max_players,
                version=version,
                host=host,
                port=port,
                last_seen_ts=time.time(),
            )

    def prune(self) -> None:
        now = time.time()
        with self._lock:
            stale = [
                name
                for name, srv in self._servers.items()
                if now - srv.last_seen_ts > self._timeout
            ]
            for name in stale:
                del self._servers[name]

    def get_channel_for_server(self, name: str) -> int | None:
        self.prune()
        with self._lock:
            srv = self._servers.get(name)
            return srv.channel_id if srv else None

    def get_server_for_channel(self, channel_id: int) -> str | None:
        self.prune()
        with self._lock:
            for srv in self._servers.values():
                if srv.channel_id == channel_id:
                    return srv.name
            return None

    def get_all_servers(self) -> list[ServerInfo]:
        self.prune()
        with self._lock:
            return list(self._servers.values())


server_registry = LiveServerRegistry()
