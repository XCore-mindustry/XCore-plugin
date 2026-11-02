import json
import socket
import urllib.error
import urllib.request

from .settings import CommandError


TRANSACTION_PROBE = """
const session = db.getMongo().startSession();
try {
  session.startTransaction();
  session.getDatabase("xcore_test").getCollection("tx_probe").insertOne({ts: new Date()});
  session.abortTransaction();
  print("OK");
} catch (err) {
  if (session.inTransaction()) session.abortTransaction();
  print("FAIL");
  quit(1);
} finally {
  session.endSession();
}
"""


def published_address(bindings, port):
    entries = bindings.get(f"{port}/tcp") or []
    if not entries:
        raise CommandError("Required port is not published.")
    entry = entries[0]
    host = entry["HostIp"]
    return ({"0.0.0.0": "127.0.0.1", "::": "::1", "": "127.0.0.1"}.get(host, host),
            int(entry["HostPort"]))


def http_ready(bindings, port, path, timeout):
    host, published = published_address(bindings, port)
    host = f"[{host}]" if ":" in host else host
    # Local published services should not go through HTTP_PROXY.
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    with opener.open(f"http://{host}:{published}{path}", timeout=timeout) as response:
        if response.status != 200:
            raise CommandError("Readiness endpoint failed.")


def diagnostics(args, compose):
    checks = []

    def check(name, probe):
        try:
            probe()
            checks.append({"name": name, "status": "ok"})
        except (CommandError, OSError, urllib.error.URLError, ValueError, KeyError):
            checks.append({"name": name, "status": "failed"})

    def expect_ok(output):
        if output.strip() != "OK":
            raise CommandError("Probe failed.")

    try:
        config = json.loads(compose.compose("config", "--format", "json", timeout=args.timeout))
        ids = compose.compose("ps", "-q", "--all", timeout=args.timeout).split()
        containers = json.loads(compose.run(["docker", "inspect", *ids], timeout=args.timeout)) if ids else []
        by_service = {c["Config"]["Labels"]["com.docker.compose.service"]: c for c in containers}
        services = config["services"]
        expected = set(args.expected or (name for name, c in by_service.items() if c["State"]["Running"]))
        if not expected:
            checks.append({"name": "stack", "status": "failed"})
        pending = list(expected)
        while pending:
            for dependency in services[pending.pop()].get("depends_on", {}):
                if dependency not in expected:
                    expected.add(dependency)
                    pending.append(dependency)

        for name in services:
            if name not in expected:
                checks.append({"name": name, "status": "skipped"})
                continue
            container = by_service.get(name)
            if name in ("mongo-init", "web-seed"):
                good = container and container["State"]["Status"] == "exited" and container["State"]["ExitCode"] == 0
                checks.append({"name": name, "status": "ok" if good else "failed"})
                continue
            if not container or not container["State"]["Running"]:
                checks.append({"name": name, "status": "failed"})
                continue
            health = container["State"].get("Health", {}).get("Status")
            checks.append({"name": name, "status": "ok" if health in (None, "healthy") else "failed"})
            container_id = container["Id"]
            bindings = container["NetworkSettings"]["Ports"]
            if name == "redis":
                def redis_probe():
                    if compose.run(["docker", "exec", container_id, "redis-cli", "ping"], timeout=args.timeout).strip() != "PONG":
                        raise CommandError("Redis ping failed.")
                check("redisPing", redis_probe)
            elif name == "mongo":
                check("mongoReplicaSet", lambda: expect_ok(compose.run([
                    "docker", "exec", container_id, "mongosh", "--quiet", "--eval",
                    'if (rs.status().ok === 1 && db.hello().isWritablePrimary) print("OK"); else quit(1);',
                ], timeout=args.timeout)))
                check("mongoTransactions", lambda: expect_ok(compose.run([
                    "docker", "exec", container_id, "mongosh", "--quiet", "--eval", TRANSACTION_PROBE,
                ], timeout=args.timeout)))
            elif name in ("game",):
                def socket_probe():
                    with socket.create_connection(published_address(bindings, 6567), timeout=args.timeout):
                        pass
                check("mindustrySocket", socket_probe)
            elif name in ("metrics-gateway", "prometheus", "grafana", "web"):
                port, path = {"metrics-gateway": (9100, "/ready"), "prometheus": (9090, "/-/ready"),
                              "grafana": (3000, "/api/health"), "web": (3000, "/en/me")}[name]
                check(f"{name}Ready", lambda: http_ready(bindings, port, path, args.timeout))
    except (CommandError, ValueError, KeyError, TypeError):
        checks.append({"name": "compose", "status": "failed"})

    ok = not any(c["status"] == "failed" for c in checks)
    report = {"ok": ok, "checks": checks}
    if args.json:
        print(json.dumps(report))
    else:
        for item in checks:
            print(f"{item['name']}: {item['status'].upper()}")
        print("Diagnostics passed." if ok else "Diagnostics failed.")
    return 0 if ok else 1
