#!/usr/bin/env bash
set -euo pipefail
mkdir -p /server/config/mods /server/config/maps /server/config/saves
# Every selected jar was validated and staged by ./dev; this image knows no plugin names.
find /server/config/mods -maxdepth 1 -name '*.jar' \( -type f -o -type l \) -delete
for jar in /staged-mods/*.jar; do
    [ -f "$jar" ] || continue
    cp "$jar" /server/config/mods/
done
for seed in /server/config-seed/*.toml; do
    [ -f "$seed" ] || continue
    name=$(basename "$seed")
    live="/server/config/$name"
    if [ ! -e "$live" ]; then
        cp "$seed" "$live"
    elif ! cmp -s "$seed" "$live"; then
        echo "[dev] config/$name differs from its seed; the live file is preserved. Use ./dev config diff/apply for supported config files." >&2
    fi
done
exec "$@"
