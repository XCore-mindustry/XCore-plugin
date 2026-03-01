# Standalone Bot Canary Rollout Runbook

This runbook describes staged rollout from embedded Java Discord integration to standalone Python bot.

## Prerequisites

- Java plugin includes transport cutover commands (`transport-*`).
- Redis and Mongo are reachable from game server and bot.
- `services/discord-bot/.env` is created from `.env.example`.

## Local smoke

```bash
cd services/discord-bot
cp .env.example .env
docker compose up --build -d
docker compose logs -f bot
```

## Stage 1 — Shadow publish only

On Java plugin server console:

```text
transport-mode dual
transport-cutover publish on
transport-cutover read-only off
transport-cutover rpc off
transport-cutover mutating off
transport-status
transport-metrics
```

Goal: ensure Redis publish path is healthy, no DLQ spikes.

## Stage 2 — Read-only consume

```text
transport-cutover read-only on
transport-stage-gate read-only
transport-canary-check
transport-go-no-go
```

Goal: Discord chat/log flows consumed from Redis are stable.

## Stage 3 — RPC cutover (`maps/remove-map`)

```text
transport-cutover rpc on
transport-stage-gate rpc
transport-canary-check
transport-go-no-go
```

Goal: `!maps` and `!remove-map` succeed with low timeout rate.

## Stage 4 — Mutating consume

```text
transport-cutover mutating on
transport-stage-gate mutating
transport-canary-check
transport-go-no-go
```

Goal: moderation/admin actions stable under idempotent semantics.

## Rollback

If canary health fails:

```text
transport-cutover all off
transport-mode sock
transport-reload
```

Then inspect:

```text
transport-metrics
transport-status
```
