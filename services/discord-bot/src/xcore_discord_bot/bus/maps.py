"""Maps: listing, uploading, removing and inspecting maps on a server."""

from __future__ import annotations

import json

from xcore_protocol.generated.maps import (
    MapsInspectResponseV1,
    MapsListRequestV1,
    MapsListResponseV1,
    MapsRemoveRequestV1,
    MapsRemoveResponseV1,
    MapsUploadRequestV1,
    MapsUploadResponseV1,
)
from xcore_protocol.generated.shared import MapEntryV1

from ..protocol_outbound import (
    build_maps_inspect_request,
    build_maps_list_request,
    build_maps_remove_request,
    build_maps_upload_request,
)
from .transport import RedisTransport

MAP_INSPECT_TTL_MS = 60_000


class MapsBus:
    def __init__(self, transport: RedisTransport) -> None:
        self._transport = transport

    async def rpc_maps_list(self, server: str, timeout_ms: int) -> list[dict[str, str]]:
        body = await self._transport.rpc_request(
            server=server,
            rpc_type=MapsListRequestV1.MESSAGE_TYPE,
            payload=build_maps_list_request(server=server).to_payload(),
            timeout_ms=timeout_ms,
        )

        payload_json = body.get("payload_json", "{}")
        payload = json.loads(payload_json)
        response = MapsListResponseV1.from_payload(payload)
        return [self._normalize_map_entry_v1(entry) for entry in response.maps]

    async def rpc_upload_maps(
        self, *, server: str, files: list[dict[str, object]], timeout_ms: int
    ) -> MapsUploadResponseV1:
        body = await self._transport.rpc_request(
            server=server,
            rpc_type=MapsUploadRequestV1.MESSAGE_TYPE,
            payload=build_maps_upload_request(server, files).to_payload(),
            timeout_ms=timeout_ms,
        )
        response = MapsUploadResponseV1.from_payload(
            json.loads(body.get("payload_json", "{}"))
        )
        expected = [item.get("fileName", item.get("filename")) for item in files]
        if (
            response.server != server
            or [result.fileName for result in response.results] != expected
        ):
            raise ValueError(
                "Map upload response does not match the requested files/server"
            )
        return response

    async def rpc_remove_map(self, server: str, file_name: str, timeout_ms: int) -> str:
        body = await self._transport.rpc_request(
            server=server,
            rpc_type=MapsRemoveRequestV1.MESSAGE_TYPE,
            payload=build_maps_remove_request(
                server=server, file_name=file_name
            ).to_payload(),
            timeout_ms=timeout_ms,
        )

        payload_json = body.get("payload_json", "{}")
        payload = json.loads(payload_json)
        response = MapsRemoveResponseV1.from_payload(payload)
        return response.result

    async def rpc_inspect_map(
        self, *, server: str, url: str, file_name: str, profile: str | None = None
    ) -> MapsInspectResponseV1:
        request = build_maps_inspect_request(
            server=server, url=url, file_name=file_name, profile=profile
        )
        body = await self._transport.rpc_request(
            server=server,
            rpc_type=request.MESSAGE_TYPE,
            payload=request.to_payload(),
            timeout_ms=MAP_INSPECT_TTL_MS,
        )
        response = MapsInspectResponseV1.from_payload(
            json.loads(body.get("payload_json", "{}"))
        )
        if response.server != server:
            raise ValueError("Map inspection response does not match request server")
        return response

    @staticmethod
    def _normalize_map_entry_v1(entry: MapEntryV1) -> dict[str, str]:
        return {
            "name": entry.name,
            "file_name": entry.fileName,
            "author": entry.author,
            "width": str(entry.width) if entry.width is not None else "",
            "height": str(entry.height) if entry.height is not None else "",
            "file_size_bytes": str(entry.fileSizeBytes)
            if entry.fileSizeBytes is not None
            else "",
            "like": str(entry.like) if entry.like is not None else "",
            "dislike": str(entry.dislike) if entry.dislike is not None else "",
            "reputation": str(entry.reputation) if entry.reputation is not None else "",
            "popularity": str(entry.popularity) if entry.popularity is not None else "",
            "interest": str(entry.interest) if entry.interest is not None else "",
            "game_mode": entry.gameMode if entry.gameMode is not None else "",
            **(
                {
                    "uploaded_by_discord_id": entry.discordUpload.uploadedBy.discordId,
                    "uploaded_by_name": entry.discordUpload.uploadedBy.discordUsername
                    or "",
                    "uploaded_at": str(entry.discordUpload.uploadedAt),
                    "uploaded_by_handle": entry.discordUpload.uploadedByHandle or "",
                    "creator_handle": entry.discordUpload.creatorHandle or "",
                    "creator_discord_id": entry.discordUpload.creator.discordId
                    if entry.discordUpload.creator
                    else "",
                    "creator_name": entry.discordUpload.creator.discordUsername or ""
                    if entry.discordUpload.creator
                    else "",
                }
                if entry.discordUpload
                else {}
            ),
        }
