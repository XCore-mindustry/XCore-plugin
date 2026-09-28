#!/usr/bin/env bash
set -e

TOKEN="${DISCORD_BOT_TOKEN:-}"

if [ -z "$TOKEN" ] || [ "$TOKEN" = "your_discord_bot_token" ] || [ "$TOKEN" = "YOUR_DISCORD_BOT_TOKEN" ]; then
    echo "================================================================================"
    echo " [XCore-discord-bot] NOTICE: DISCORD_BOT_TOKEN is unset or using dummy default."
    echo " Bot will enter IDLE mode (sleep infinity) to prevent restart crash loops."
    echo " To enable the bot:"
    echo "   1. Set DISCORD_BOT_TOKEN in .env"
    echo "   2. Restart: ./dev.sh restart discord-bot"
    echo "================================================================================"
    exec sleep infinity
fi

exec "$@"
