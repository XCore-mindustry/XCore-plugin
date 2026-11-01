FROM ghcr.io/astral-sh/uv:python3.14-bookworm-slim AS builder

ENV UV_PYTHON_DOWNLOADS=never
WORKDIR /app

# Build with the monorepo root as context; the lock covers every workspace member.
COPY pyproject.toml uv.lock ./
COPY protocol ./protocol
COPY services/metrics-gateway ./services/metrics-gateway
COPY services/discord-bot/pyproject.toml services/discord-bot/README.md ./services/discord-bot/
RUN uv sync --locked --no-dev --no-editable --package xcore-metrics-gateway

FROM python:3.14-slim-bookworm AS runtime

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PATH="/app/.venv/bin:${PATH}"
WORKDIR /app
COPY --from=builder /app/.venv /app/.venv

EXPOSE 9100
CMD ["xcore-metrics-gateway"]
