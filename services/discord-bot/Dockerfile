# Multi-stage Dockerfile for XCore Discord Bot
FROM ghcr.io/astral-sh/uv:python3.14-bookworm-slim AS builder

WORKDIR /app

# git and ca-certificates are required for uv to resolve git-based dependencies (e.g. xcore-protocol)
RUN apt-get update && apt-get install -y --no-install-recommends \
    git \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# Until xcore-protocol v0.17.0 is released, pyproject.toml builds it from ../xcore-protocol.
# Pass that checkout as the `xcore_protocol` build context; only what its wheel needs is copied.
# uv installs it non-editably, so the runtime image needs nothing from /xcore-protocol.
COPY --from=xcore_protocol pyproject.toml README.md /xcore-protocol/
COPY --from=xcore_protocol python/xcore_protocol /xcore-protocol/python/xcore_protocol

COPY pyproject.toml uv.lock README.md ./
COPY src ./src

RUN uv sync --locked --no-dev

FROM python:3.14-slim-bookworm AS runtime

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PATH="/app/.venv/bin:${PATH}"

WORKDIR /app

COPY --from=builder /app/.venv /app/.venv
COPY --from=builder /app/src /app/src
COPY --from=builder /app/README.md /app/README.md
COPY --from=builder /app/pyproject.toml /app/pyproject.toml

COPY docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh
RUN chmod +x /usr/local/bin/docker-entrypoint.sh

ENTRYPOINT ["docker-entrypoint.sh"]
CMD ["xcore-discord-bot"]
