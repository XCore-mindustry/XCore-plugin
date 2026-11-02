from __future__ import annotations

import json

from bot_fakes import make_settings
from fakeredis.aioredis import FakeRedis

from xcore_discord_bot.bus.transport import RedisTransport

STREAM = "xcore:evt:test"
GROUP = "xcore:cg:test"


async def _transport() -> RedisTransport:
    transport = RedisTransport(make_settings())
    transport._redis = FakeRedis(decode_responses=True)
    await transport._ensure_group(STREAM, GROUP)
    return transport


async def _deliver(transport: RedisTransport, fields: dict[str, str], callback) -> None:
    message_id = await transport.redis.xadd(STREAM, fields)
    await transport._process_event_message(
        stream=STREAM,
        group=GROUP,
        message_id=message_id,
        fields=fields,
        callback=callback,
        parse_payload=lambda payload: payload,
        parse_fields=None,
        skip_discord_producer=False,
        max_attempts=5,
        source="live",
    )


async def test_a_callback_sees_its_event_and_a_stable_delivery_identity() -> None:
    transport = await _transport()
    seen = []

    async def callback(event) -> None:
        seen.append(transport.current_delivery())

    fields = {"event_id": "evt-1", "payload_json": json.dumps({"n": 1})}
    await _deliver(transport, fields, callback)
    await _deliver(transport, {"payload_json": json.dumps({"n": 2})}, callback)

    (first_event, first_identity), (second_event, second_identity) = seen
    assert first_event == {"n": 1}
    assert first_identity == '["event","evt-1"]'
    # Without an event id the stream message itself identifies the delivery.
    assert second_event == {"n": 2}
    assert json.loads(second_identity)[:2] == ["stream", STREAM]
    assert transport.current_delivery() is None
