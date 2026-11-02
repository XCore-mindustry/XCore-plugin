#!/usr/bin/env bash
set -euo pipefail
umask 077

action="$1"
name="$2"
config_dir="${DEV_CONFIG_DIR:-/server/config}"
classpath="${DEV_CONFIG_CLASSPATH:-/server/server.jar:${config_dir}/mods/*}"
validator="org.xcore.plugin.config.ConfigValidationCli"

validate() {
    java -cp "$classpath" "$validator" "$name" "$1" >/dev/null
}

case "$name" in
    xcore|sentinel) live="${config_dir}/${name}.toml" ;;
    secrets) live=$(java -cp "$classpath" "$validator" resolve-secrets "$config_dir") ;;
    *) echo "Unknown config name." >&2; exit 2 ;;
esac

if [ "$action" = read ]; then
    [ ! -L "$live" ] && [ -f "$live" ] || { echo "Live config is missing or a symlink." >&2; exit 1; }
    cat "$live"
    exit
fi

parent="$(dirname "$live")"
backups="${parent}/.config-backups/${name}"
[ ! -L "${parent}/.config-backups" ] && [ ! -L "$backups" ] || { echo "Backup directory must not be a symlink." >&2; exit 1; }
lock="${parent}/.config-${name}.lock"
mkdir "$lock" || { echo "Another config operation is running." >&2; exit 1; }
temporary=""
trap 'rm -f -- "$temporary"; rmdir "$lock"' EXIT
[ ! -L "$live" ] && [ -f "$live" ] || { echo "Live config is missing or a symlink." >&2; exit 1; }
temporary=$(mktemp "${parent}/.${name}.XXXXXX.toml")
case "$action" in
    apply) cat > "$temporary" ;;
    restore)
        backup="${3:-}"
        [[ "$backup" =~ ^[0-9]{8}T[0-9]{6}Z\.[a-zA-Z0-9]+\.bak$ ]] || { echo "Invalid backup ID." >&2; exit 2; }
        [ ! -L "${backups}/${backup}" ] && [ -f "${backups}/${backup}" ] || { echo "Backup not found." >&2; exit 1; }
        cat "${backups}/${backup}" > "$temporary"
        ;;
    *) echo "Unknown config operation." >&2; exit 2 ;;
esac
validate "$temporary"
if cmp -s "$temporary" "$live"; then
    echo "Unchanged"
    exit
fi
mkdir -p "$backups"
chmod 700 "${parent}/.config-backups" "$backups"
saved=$(mktemp "${backups}/$(date -u +%Y%m%dT%H%M%SZ).XXXXXX.bak")
cp -- "$live" "$saved"
chmod 600 "$saved" "$temporary"
mv -f -- "$temporary" "$live"
echo "Backup: $(basename "$saved")"
