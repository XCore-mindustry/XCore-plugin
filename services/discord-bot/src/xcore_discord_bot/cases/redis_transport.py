"""Redis adapter for the cases core's optional transport contract.

Report flows register ``cases.report.submitted``; external bans arrive through
``moderation.ban.created``. Both hooks receive generated protocol models.
"""

from __future__ import annotations

from typing import Any

from ..bus.cases import CasesBus
from ..rpc.mindustry_rpc import MindustryRpcClient


class RedisCaseTransport:
    def __init__(self, cases: CasesBus, rpc: MindustryRpcClient) -> None:
        self._cases = cases
        self._rpc = rpc

    async def publish_case_notice(
        self, *, notice_id: str, player_uuid: str, server: str
    ) -> None:
        # Only a wake-up identifier. Mongo content, leases and delivery ACKs
        # belong to the cases core and the plugin respectively.
        await self._cases.publish_case_notice(
            notice_id=notice_id, player_uuid=player_uuid, server=server
        )

    async def rpc_case_evidence(
        self,
        *,
        server: str,
        player_uuid: str,
        chat_lines: int = 30,
        window_seconds: int = 600,
    ) -> dict[str, Any] | None:
        response = await self._rpc.rpc_case_evidence(
            server=server,
            player_uuid=player_uuid,
            chat_lines=chat_lines,
            window_seconds=window_seconds,
        )
        return response.to_payload()

    async def rpc_inspect_map(
        self,
        *,
        url: str,
        file_name: str,
        profile: str | None = None,
        server: str | None = None,
    ) -> dict[str, Any] | None:
        response = await self._rpc.rpc_inspect_map(
            url=url, file_name=file_name, profile=profile, server=server
        )
        return response.to_payload()
