FROM ghcr.io/astral-sh/uv:python3.14-bookworm-slim AS builder

ENV UV_PYTHON_DOWNLOADS=never
WORKDIR /app

# Build with the monorepo root as context; the lock covers every workspace member.
COPY pyproject.toml uv.lock ./
COPY protocol ./protocol
COPY services/discord-bot ./services/discord-bot
COPY services/metrics-gateway/pyproject.toml services/metrics-gateway/README.md ./services/metrics-gateway/
RUN uv sync --locked --no-dev --no-editable --package xcore-discord-bot

FROM python:3.14-slim-bookworm AS runtime

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PATH="/app/.venv/bin:${PATH}"
WORKDIR /app
COPY --from=builder /app/.venv /app/.venv

COPY services/discord-bot/docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh
RUN chmod +x /usr/local/bin/docker-entrypoint.sh
ENTRYPOINT ["docker-entrypoint.sh"]
CMD ["xcore-discord-bot"]
