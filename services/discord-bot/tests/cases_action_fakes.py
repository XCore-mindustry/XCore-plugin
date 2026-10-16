"""Shared adapters for real staff helpers over the cases test databases."""

import fakeredis.aioredis


class Players:
    def __init__(self, bot, db):
        self.bot, self.database = bot, db

    def __getattr__(self, name):
        return getattr(self.bot, name)

    async def find_players_by_discord_id(self, discord_id):
        return [
            await self.bot.find_player_by_uuid(row["uuid"])
            for row in self.database["players"].docs
            if row.get("discord_id") == discord_id
        ]


class Claims:
    def __init__(self):
        self.redis = fakeredis.aioredis.FakeRedis()

    async def claim_idempotency(self, key, ttl_seconds=600):
        return bool(await self.redis.set(key, "1", nx=True, ex=ttl_seconds))


def mirror_audits(bot, action_bot, database, mutations):
    append = action_bot.append_moderation_audit

    async def audit(**kwargs):
        audit_id = await append(**kwargs)
        await database["moderation_audit"].insert_one(
            mutations["moderation_audit"].docs[-1]
        )
        return audit_id

    bot.append_moderation_audit = audit
