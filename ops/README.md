# Deploying the services

The Discord bot, the metrics gateway and the website run on the game server as
systemd user units of the `xcore` user. All three are deployed the same way, by
`ops/deploy` from a developer machine:

```sh
ops/deploy <ssh-host> bot              # one service
ops/deploy <ssh-host> gateway web      # several
ops/deploy <ssh-host> all --ref <sha>  # a given commit of main, e.g. to roll back
```

The script deploys a commit of `origin/main` (the latest one by default). It
checks that commit out on the server, installs the unit from `ops/systemd/`,
restarts the service and waits for it to be healthy: the bot has to log
`Discord bot connected`, the gateway has to answer `/ready`, the website `/en`.
If the check fails, it prints the log and the previously deployed commit.

The bot and the gateway are installed from the server checkout with uv. The
website is built locally (it needs [Bun](https://bun.sh); the server has
neither Bun nor the memory for a Next.js build) and copied with rsync.

## Server layout

| Path | |
|---|---|
| `~/services/xcore` | checkout of this repository at the deployed commit |
| `~/services/venvs/discord-bot`, `metrics-gateway` | Python environments (3.14 and 3.13) |
| `~/services/web` | website build; `web.prev` is the one before it |
| `~/services/deployed/<service>` | deployed commit of each service |
| `~/.config/xcore/<service>.env` | settings and secrets: `discord-bot.env`, `metrics-gateway.env`, `web.env`, mode 0600 |
| `~/.config/systemd/user/xcore-*.service` | copies of `ops/systemd/`, written by the deploy |

The env files are read by systemd (`EnvironmentFile=`), so they hold plain
`KEY=value` lines. They are created by hand; the deploy never writes them. The
service variables are listed in each service's `.env.example`.

Linger has to be enabled for the user, so the units start at boot without a login:
`sudo loginctl enable-linger xcore`.

## Day to day

```sh
systemctl --user status xcore-discord-bot
journalctl --user -fu xcore-discord-bot        # follow the log
systemctl --user restart xcore-metrics-gateway
```

The same works for `xcore-metrics-gateway` and `xcore-web`. After editing an env
file, restart the service.
