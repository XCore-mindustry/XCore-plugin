#!/usr/bin/env bash
set -euo pipefail

MONGO_HOST="${MONGO_HOST:-mongo}"
MONGO_PORT="${MONGO_PORT:-27017}"
REPLSET_NAME="${REPLSET_NAME:-rs0}"
MAX_TRIES="${MAX_TRIES:-30}"
SLEEP_SECS="${SLEEP_SECS:-2}"

echo "[mongo-init] Probing MongoDB daemon at ${MONGO_HOST}:${MONGO_PORT}..."

tries=0
until mongosh --host "${MONGO_HOST}:${MONGO_PORT}" --quiet --eval "db.adminCommand('ping')" >/dev/null 2>&1; do
    tries=$((tries + 1))
    if [ "$tries" -ge "$MAX_TRIES" ]; then
        echo "[mongo-init] FATAL: Timed out waiting for MongoDB to start."
        exit 1
    fi
    sleep "$SLEEP_SECS"
done

echo "[mongo-init] MongoDB is accepting connections. Checking replica set status..."

IS_INITIATED=$(mongosh --host "${MONGO_HOST}:${MONGO_PORT}" --quiet --eval '
  try {
    var res = rs.status();
    print(res.ok === 1 ? "true" : "false");
  } catch (err) {
    print("false");
  }
')

if [ "$IS_INITIATED" = "true" ]; then
    echo "[mongo-init] Replica set \"${REPLSET_NAME}\" is already initiated."
else
    echo "[mongo-init] Initiating single-node replica set \"${REPLSET_NAME}\" on ${MONGO_HOST}:${MONGO_PORT}..."
    mongosh --host "${MONGO_HOST}:${MONGO_PORT}" --quiet --eval "
      rs.initiate({
        _id: '${REPLSET_NAME}',
        members: [{ _id: 0, host: '${MONGO_HOST}:${MONGO_PORT}' }]
      });
    "
fi

echo "[mongo-init] Waiting for PRIMARY election..."
tries=0
until mongosh --host "${MONGO_HOST}:${MONGO_PORT}" --quiet --eval '
  try {
    var hello = db.hello();
    print((hello.isWritablePrimary || hello.ismaster) ? "primary" : "waiting");
  } catch (err) {
    print("waiting");
  }
' | grep -q "primary"; do
    tries=$((tries + 1))
    if [ "$tries" -ge "$MAX_TRIES" ]; then
        echo "[mongo-init] FATAL: Timed out waiting for PRIMARY election."
        exit 1
    fi
    sleep "$SLEEP_SECS"
done

echo "[mongo-init] SUCCESS: MongoDB replica set \"${REPLSET_NAME}\" is healthy and PRIMARY is writable."
