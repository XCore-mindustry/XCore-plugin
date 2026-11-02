#!/usr/bin/env python3
"""
Publishes realistic mock ServerHeartbeatV1 messages to Redis for local testing.
Uses pure standard library (socket) so it has zero external dependencies.
"""

import json
import os
import socket
import sys
import time
import uuid


def parse_int_env(key: str, default: int) -> int:
    raw = os.getenv(key)
    if not raw:
        return default
    try:
        return int(raw.strip())
    except (ValueError, TypeError):
        return default


REDIS_HOST = os.getenv("REDIS_HOST", "127.0.0.1")
REDIS_PORT = parse_int_env("REDIS_PORT", 6379)

MOCK_SERVERS = [
    # PVP
    {
        "serverName": "mini-pvp",
        "players": 14,
        "maxPlayers": 25,
        "port": 7001,
        "description": "Fast-paced 1v1 and 2v2 tactical duels on arena maps",
        "map": "Crossing",
        "wave": 6,
        "mode": "pvp",
        "tps": 61,
        "host": "127.0.0.1",
    },
    {
        "serverName": "hexedcore",
        "players": 9,
        "maxPlayers": 16,
        "port": 7005,
        "description": "Battle royale: conquer hexes, expand territory, dominate",
        "map": "Hexed Isles",
        "wave": 1,
        "mode": "hexed",
        "tps": 60,
        "host": "127.0.0.1",
    },
    {
        "serverName": "rvsb",
        "players": 15,
        "maxPlayers": 16,
        "port": 7006,
        "description": "Build stations, defend flagships, engage orbital fleet combat",
        "map": "Orbit Omega",
        "wave": 1,
        "mode": "rvsb",
        "tps": 60,
        "host": "127.0.0.1",
    },
    {
        "serverName": "anarchy",
        "players": 21,
        "maxPlayers": 32,
        "port": 7010,
        "description": "No rules, high tech tier, ruthless tactical warfare",
        "map": "Wasteland",
        "wave": 14,
        "mode": "anarchy",
        "tps": 59,
        "host": "127.0.0.1",
    },
    {
        "serverName": "spacewar",
        "players": 8,
        "maxPlayers": 20,
        "port": 7011,
        "description": "Naval and orbital fleet combat with massive dreadnoughts",
        "map": "Deep Void",
        "wave": 3,
        "mode": "pvp",
        "tps": 61,
        "host": "127.0.0.1",
    },

    # SURVIVAL
    {
        "serverName": "mini-surv",
        "players": 16,
        "maxPlayers": 20,
        "port": 7002,
        "description": "Co-op classic survival with custom curated maps",
        "map": "Glacier Pass",
        "wave": 48,
        "mode": "survival",
        "tps": 60,
        "host": "127.0.0.1",
    },
    {
        "serverName": "towerdefence",
        "players": 11,
        "maxPlayers": 12,
        "port": 7009,
        "description": "Automated maze defense against relentless boss waves",
        "map": "Spiral Core",
        "wave": 31,
        "mode": "td",
        "tps": 60,
        "host": "127.0.0.1",
    },
    {
        "serverName": "asthosus",
        "players": 5,
        "maxPlayers": 16,
        "port": 7008,
        "description": "Deep planetary exploration and campaign on Serpulo",
        "map": "Ancient Ruins",
        "wave": 18,
        "mode": "survival",
        "tps": 60,
        "host": "127.0.0.1",
    },
    {
        "serverName": "hardcore",
        "players": 7,
        "maxPlayers": 16,
        "port": 7012,
        "description": "Permadeath waves and extreme resource constraints",
        "map": "Inferno Peak",
        "wave": 65,
        "mode": "hardcore",
        "tps": 58,
        "host": "127.0.0.1",
    },
    {
        "serverName": "sandbox",
        "players": 6,
        "maxPlayers": 20,
        "port": 7013,
        "description": "Unlimited building, testing, schematics, and experimentation",
        "map": "Testing Ground",
        "wave": 1,
        "mode": "sandbox",
        "tps": 62,
        "host": "127.0.0.1",
    },

    # SPECIAL
    {
        "serverName": "mini-attack",
        "players": 12,
        "maxPlayers": 20,
        "port": 7003,
        "description": "Assault fortified enemy bases and cores on compact maps",
        "map": "Iron Fortress",
        "wave": 9,
        "mode": "attack",
        "tps": 60,
        "host": "127.0.0.1",
    },
    {
        "serverName": "siege",
        "players": 14,
        "maxPlayers": 20,
        "port": 7007,
        "description": "Coordinate base defense against legendary siege engines",
        "map": "Citadel",
        "wave": 37,
        "mode": "siege",
        "tps": 60,
        "host": "127.0.0.1",
    },
    {
        "serverName": "event",
        "players": 26,
        "maxPlayers": 30,
        "port": 7004,
        "description": "Weekend community tournament and custom minigames",
        "map": "Grand Arena",
        "wave": 1,
        "mode": "event",
        "tps": 61,
        "host": "127.0.0.1",
    },
    {
        "serverName": "bossrush",
        "players": 8,
        "maxPlayers": 16,
        "port": 7014,
        "description": "Continuous boss titan encounters with mythic rewards",
        "map": "Titan Crater",
        "wave": 22,
        "mode": "boss",
        "tps": 60,
        "host": "127.0.0.1",
    },
    {
        "serverName": "strategy",
        "players": 11,
        "maxPlayers": 20,
        "port": 7015,
        "description": "Sector capture RTS with macro logistics and frontline expansion",
        "map": "Archipelago",
        "wave": 4,
        "mode": "rts",
        "tps": 60,
        "host": "127.0.0.1",
    },
]


def encode_resp_command(parts: list[str]) -> bytes:
    """Encodes a command into Redis RESP array format."""
    buf = [f"*{len(parts)}\r\n".encode()]
    for p in parts:
        data = p.encode()
        buf.append(f"${len(data)}\r\n".encode())
        buf.append(data)
        buf.append(b"\r\n")
    return b"".join(buf)


def current_time_ms() -> int:
    try:
        return int(time.time() * 1000)
    except (ValueError, TypeError, OverflowError):
        return 0


def publish_heartbeats(sock: socket.socket) -> None:
    now_ms = current_time_ms()
    for s in MOCK_SERVERS:
        event_id = str(uuid.uuid4())
        payload = {
            "messageType": "server.heartbeat",
            "messageVersion": 1,
            "serverName": s["serverName"],
            "discordChannelId": 0,
            "players": s["players"],
            "maxPlayers": s["maxPlayers"],
            "version": "160.5",
            "host": s["host"],
            "port": s["port"],
            "description": s["description"],
            "map": s["map"],
            "wave": s["wave"],
            "mode": s["mode"],
            "tps": s["tps"],
        }
        cmd = [
            "XADD",
            "xcore:evt:server:heartbeat",
            "*",
            "schema_version", "1",
            "event_type", "server.heartbeat",
            "event_id", event_id,
            "idempotency_key", f"mock.heartbeat:{s['serverName']}:{now_ms}",
            "producer", f"server:{s['serverName']}",
            "created_at", str(now_ms),
            "expires_at", str(now_ms + 60000),
            "server", str(s["serverName"]),
            "payload_json", json.dumps(payload),
        ]
        sock.sendall(encode_resp_command(cmd))
        # Drain single RESP line response
        sock.recv(128)

    print(f"[{time.strftime('%X')}] Published {len(MOCK_SERVERS)} mock server heartbeats to Redis.")


def main():
    print(f"Connecting to Redis at {REDIS_HOST}:{REDIS_PORT}...")
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    try:
        sock.connect((REDIS_HOST, REDIS_PORT))
        # PING check
        sock.sendall(encode_resp_command(["PING"]))
        resp = sock.recv(32)
        if b"+PONG" not in resp:
            print(f"Unexpected Redis ping response: {resp}")
            sys.exit(1)
        print("Connected successfully.")

        publish_heartbeats(sock)
    finally:
        sock.close()


if __name__ == "__main__":
    main()
