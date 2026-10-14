from __future__ import annotations

import logging

import discord

from ..permissions import (
    admin_role_ids,
    general_admin_role_ids,
    head_admin_role_ids,
    member_role_ids,
)
from ._privacy import log_failure
from .access import staff_roles
from .model import CaseKind
from .registry import FLAG_FIELDS
from .threads import FORUM_TAGS

logger = logging.getLogger(__name__)


async def initialize_flows(service) -> None:
    """Enable each flow whose flag is on and whose Discord side passes the checks of 8.9.

    A flow that fails stays off with its reasons logged and kept for ``/cases panel publish``;
    nothing here can take the rest of the bot down.
    """
    registered = {
        flow.kind: flow for flow in service.registry.configured(service.settings)
    }
    for kind, flag in FLAG_FIELDS.items():
        service.registry.disable(kind)
        if not getattr(service.settings, flag):
            continue
        if not service.storage_available:
            reason = "Cases storage is unavailable"
            service.registry.disable(kind, (reason,))
            logger.warning("Cases flow %s disabled: %s", kind, reason)
            continue
        if kind not in registered:
            reason = "Flow module is not installed"
            service.registry.disable(kind, (reason,))
            logger.warning("Cases flow %s disabled: %s", kind, reason)
            continue
        try:
            reasons = await validate_flow(service, kind)
        except Exception as error:
            log_failure(logger, kind, "Cases startup check failed for %s", kind)
            reasons = [f"Discord setup could not be checked ({type(error).__name__})"]
        if reasons:
            service.registry.disable(kind, reasons)
            logger.warning("Cases flow %s disabled: %s", kind, "; ".join(reasons))
        else:
            if kind == CaseKind.MAP:
                service.maps_enabled_at = service.now()
            service.registry.enable(kind)


async def validate_flow(service, kind: CaseKind) -> list[str]:
    settings, threads = service.settings, service.threads
    reasons: list[str] = []
    forum_key = (
        "DISCORD_MAPS_FORUM_ID"
        if kind == CaseKind.MAP
        else "CASES_SENIOR_FORUM_ID"
        if kind == CaseKind.COMPLAINT
        else "CASES_STAFF_FORUM_ID"
    )
    forum_id = threads.forum_id(kind)
    parent_key = (
        "CASES_COMPLAINT_THREAD_CHANNEL_ID"
        if kind == CaseKind.COMPLAINT
        else "CASES_PANEL_CHANNEL_ID"
    )
    parent_id = threads.user_parent_id(kind)
    if not forum_id:
        reasons.append(f"{forum_key} is not configured")
    if kind != CaseKind.MAP and not settings.cases_panel_channel_id:
        reasons.append("CASES_PANEL_CHANNEL_ID is not configured")
    if kind != CaseKind.MAP and not parent_id:
        reasons.append(f"{parent_key} is not configured")
    if reasons:
        return reasons
    try:
        forum = await threads.channel(forum_id)
    except (discord.NotFound, discord.Forbidden):
        return [f"{forum_key}: the channel does not exist or the bot cannot see it"]
    if forum.type != discord.ChannelType.forum:
        return [f"{forum_key} must be a forum channel"]
    guild = forum.guild
    if settings.discord_guild_id and guild.id != settings.discord_guild_id:
        reasons.append(f"{forum_key} belongs to another guild")
    if guild.me is None:
        return reasons + ["Bot guild membership is unavailable"]
    bot_permissions = forum.permissions_for(guild.me)
    required = (
        "view_channel",
        "send_messages",
        "send_messages_in_threads",
        "read_message_history",
        "manage_threads",
    )
    for name in required:
        if not getattr(bot_permissions, name):
            reasons.append(f"{forum_key}: bot needs {name}")
    if kind != CaseKind.MAP:
        senior_ids = set(head_admin_role_ids(settings))
        bot_role_ids = {
            role.id
            for role in guild.roles
            if role.is_bot_managed() and role.id in member_role_ids(guild.me)
        }
        if kind == CaseKind.COMPLAINT and senior_ids & set(admin_role_ids(settings)):
            reasons.append(
                "Complaints require a senior role distinct from the ordinary admin role"
            )
        if forum.permissions_for(guild.default_role).view_channel:
            reasons.append(f"{forum_key} is visible to @everyone")
        expected = (
            senior_ids
            if kind == CaseKind.COMPLAINT
            else set(admin_role_ids(settings) + general_admin_role_ids(settings))
        )
        for role_id in expected:
            role = guild.get_role(role_id)
            if role is None or not forum.permissions_for(role).view_channel:
                reasons.append(
                    f"{forum_key}: configured staff role {role_id} cannot view the forum"
                )
        if kind == CaseKind.COMPLAINT:
            for role in guild.roles:
                if (
                    role.id not in senior_ids
                    and role.id not in bot_role_ids
                    and role != guild.default_role
                    and forum.permissions_for(role).view_channel
                ):
                    reasons.append(
                        f"{forum_key} is visible to non-senior role {role.id}"
                    )
        try:
            parent = await threads.channel(parent_id)
        except (discord.NotFound, discord.Forbidden):
            return reasons + [
                f"{parent_key}: the channel does not exist or the bot cannot see it"
            ]
        if parent.type != discord.ChannelType.text:
            reasons.append(f"{parent_key} must be a text channel")
        elif parent.guild.id != guild.id:
            reasons.append(f"{parent_key} must be in the same guild as {forum_key}")
        else:
            perms = parent.permissions_for(guild.me)
            for name in (
                "view_channel",
                "send_messages",
                "send_messages_in_threads",
                "read_message_history",
                "create_private_threads",
                "manage_threads",
            ):
                if not getattr(perms, name):
                    reasons.append(f"{parent_key}: bot needs {name}")
            everyone = parent.permissions_for(guild.default_role)
            if not everyone.view_channel or not everyone.send_messages_in_threads:
                reasons.append(
                    f"{parent_key}: members must be able to view and write in their threads"
                )
            if kind == CaseKind.COMPLAINT:
                for role_id in senior_ids:
                    role = guild.get_role(role_id)
                    if role is None or not parent.permissions_for(role).view_channel:
                        reasons.append(
                            f"{parent_key}: senior role {role_id} cannot view the parent"
                        )
                for role in guild.roles:
                    if (
                        role.id in set(staff_roles(settings)) - senior_ids
                        and parent.permissions_for(role).view_channel
                    ):
                        reasons.append(
                            f"{parent_key} is visible to non-senior staff role {role.id}"
                        )
                    if (
                        role.id not in senior_ids
                        and role.id not in bot_role_ids
                        and parent.permissions_for(role).manage_threads
                    ):
                        reasons.append(
                            f"{parent_key}: non-senior role {role.id} has Manage Threads"
                        )
        if not reasons:
            names = {tag.name for tag in forum.available_tags}
            missing = [name for name in FORUM_TAGS if name not in names]
            if missing and not bot_permissions.manage_channels:
                reasons.append(
                    f"{forum_key}: create the tags {', '.join(missing)} or give the bot"
                    " manage_channels to create them"
                )
            elif missing:
                await forum.edit(
                    available_tags=[
                        *forum.available_tags,
                        *(discord.ForumTag(name=name) for name in missing),
                    ]
                )
    return reasons
