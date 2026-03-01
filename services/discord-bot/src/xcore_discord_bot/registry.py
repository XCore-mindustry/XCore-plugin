import time
from threading import Lock


class ServerInfo:
    def __init__(
        self, name: str, channel_id: int, players: int, max_players: int, version: str
    ):
        self.name = name
        self.channel_id = channel_id
        self.players = players
        self.max_players = max_players
        self.version = version
        self.last_seen_ts = time.time()


class LiveServerRegistry:
    def __init__(self, timeout_sec: int = 90):
        self._servers: dict[str, ServerInfo] = {}
        self._lock = Lock()
        self._timeout = timeout_sec

    def update_server(
        self, name: str, channel_id: int, players: int, max_players: int, version: str
    ) -> None:
        with self._lock:
            if name in self._servers:
                srv = self._servers[name]
                srv.channel_id = channel_id
                srv.players = players
                srv.max_players = max_players
                srv.version = version
                srv.last_seen_ts = time.time()
            else:
                self._servers[name] = ServerInfo(
                    name, channel_id, players, max_players, version
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
