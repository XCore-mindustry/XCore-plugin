"""Bounded readiness wait for CI smoke tests, not part of the dev CLI."""
import subprocess
import time

for attempt in range(60):
    result = subprocess.run(["./dev", "check", "--json"], capture_output=True)
    if result.returncode == 0:
        print(result.stdout.decode())
        break
    time.sleep(2)
else:
    raise SystemExit("Development stack did not become ready")
