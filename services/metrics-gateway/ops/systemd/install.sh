#!/usr/bin/env bash
set -euo pipefail

SERVICE_NAME="xcore-metrics-gateway"

if [[ "$(id -u)" -ne 0 ]]; then
  printf 'Run this installer with sudo.\n' >&2
  exit 1
fi

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"

REPO_OWNER="$(stat -c '%U' "${PROJECT_DIR}")"
REPO_GROUP="$(stat -c '%G' "${PROJECT_DIR}")"
SERVICE_USER="${SERVICE_USER:-${REPO_OWNER}}"
SERVICE_GROUP="${SERVICE_GROUP:-${REPO_GROUP}}"
ENV_DIR="${ENV_DIR:-/etc/xcore}"
ENV_FILE="${ENV_FILE:-${ENV_DIR}/${SERVICE_NAME}.env}"
UNIT_FILE="${UNIT_FILE:-/etc/systemd/system/${SERVICE_NAME}.service}"
UV_BIN="${UV_BIN:-$(command -v uv || true)}"

if [[ -z "${UV_BIN}" && -n "${SUDO_USER:-}" ]]; then
  UV_BIN="$(runuser -u "${SUDO_USER}" -- sh -lc 'command -v uv' 2>/dev/null || true)"
fi

if [[ -z "${UV_BIN}" ]]; then
  printf 'uv was not found in PATH. Install uv first, then rerun this installer.\n' >&2
  exit 1
fi

if ! id "${SERVICE_USER}" >/dev/null 2>&1; then
  printf 'Service user %s does not exist. Set SERVICE_USER or run from a repo owned by an existing user.\n' "${SERVICE_USER}" >&2
  exit 1
fi

if ! getent group "${SERVICE_GROUP}" >/dev/null 2>&1; then
  printf 'Service group %s does not exist. Set SERVICE_GROUP or run from a repo owned by an existing group.\n' "${SERVICE_GROUP}" >&2
  exit 1
fi

install -d -m 0755 "${ENV_DIR}"

if [[ ! -f "${ENV_FILE}" ]]; then
  TMP_ENV="$(mktemp)"
  while IFS= read -r line; do
    if [[ "${line}" == REDIS_URL=* && -n "${REDIS_URL:-}" ]]; then
      printf 'REDIS_URL=%s\n' "${REDIS_URL}"
    else
      printf '%s\n' "${line}"
    fi
  done < "${PROJECT_DIR}/.env.example" > "${TMP_ENV}"
  install -m 0640 -o root -g "${SERVICE_GROUP}" "${TMP_ENV}" "${ENV_FILE}"
  rm -f "${TMP_ENV}"
fi

if [[ "${SERVICE_USER}" == "root" ]]; then
  "${UV_BIN}" sync --locked --no-dev --project "${PROJECT_DIR}"
else
  runuser -u "${SERVICE_USER}" -- "${UV_BIN}" sync --locked --no-dev --project "${PROJECT_DIR}"
fi

cat > "${UNIT_FILE}" <<EOF
[Unit]
Description=XCore Metrics Gateway
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=${SERVICE_USER}
Group=${SERVICE_GROUP}
WorkingDirectory=${PROJECT_DIR}
EnvironmentFile=${ENV_FILE}
ExecStart=${PROJECT_DIR}/.venv/bin/xcore-metrics-gateway
Restart=on-failure
RestartSec=5
TimeoutStopSec=15
KillSignal=SIGINT

[Install]
WantedBy=multi-user.target
EOF

chmod 0644 "${UNIT_FILE}"
systemctl daemon-reload
systemctl enable --now "${SERVICE_NAME}"

printf 'Installed %s.\n' "${SERVICE_NAME}"
printf 'Unit: %s\n' "${UNIT_FILE}"
printf 'Environment: %s\n' "${ENV_FILE}"
printf 'Service user: %s:%s\n' "${SERVICE_USER}" "${SERVICE_GROUP}"
printf 'Check status: systemctl status %s\n' "${SERVICE_NAME}"
