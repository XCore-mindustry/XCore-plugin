from __future__ import annotations

import struct
import zlib

import pytest

from xcore_discord_bot.cases.msav import (
    MAX_INFLATED_BYTES,
    InvalidMsav,
    data_patches,
    validate_msav,
)


def mutf8(text):
    units = text.encode("utf-16-be")
    result = bytearray()
    for i in range(0, len(units), 2):
        value = int.from_bytes(units[i : i + 2], "big")
        if 0 < value < 128:
            result.append(value)
        elif value < 2048:
            result.extend((0xC0 | value >> 6, 0x80 | value & 63))
        else:
            result.extend(
                (0xE0 | value >> 12, 0x80 | value >> 6 & 63, 0x80 | value & 63)
            )
    return struct.pack(">H", len(result)) + result


def msav(*, meta=None, version=13, tail=b"opaque gameplay data", magic=b"MSAV"):
    meta = {"name": "Arena"} if meta is None else meta
    region = struct.pack(">h", len(meta)) + b"".join(
        mutf8(k) + mutf8(v) for k, v in meta.items()
    )
    return zlib.compress(
        magic + struct.pack(">ii", version, len(region)) + region + tail
    )


def test_msav_header_meta_modified_utf8_and_opaque_gameplay():
    data = msav(
        meta={"name": "Карта 🐈\0", "rules": "this is NOT executable JSON"},
        tail=b"hostile opaque entities",
    )
    info = validate_msav(data, "arena.MSAV")
    assert info.version == 13 and info.meta["name"] == "Карта 🐈\0"
    assert info.meta["rules"] == "this is NOT executable JSON"
    assert len(info.sha256) == 64 and info.size == len(data)


@pytest.mark.parametrize("version", range(1, 14))
def test_msav_supported_versions(version):
    assert validate_msav(msav(version=version), "a.msav").version == version


@pytest.mark.parametrize(
    "data,code",
    [
        (b"", "size"),
        (b"not zlib", "zlib"),
        (msav()[:-2], "truncated"),
        (zlib.compress(b"MSA"), "truncated"),
        (msav(magic=b"NOPE"), "magic"),
        (msav(version=14), "version"),
        (msav(version=0), "version"),
        (msav() + b"extra", "zlib"),
        (msav() + msav(), "zlib"),
        (zlib.compress(b"MSAV" + struct.pack(">ii", 13, 100) + b"\0\0"), "truncated"),
        (zlib.compress(b"MSAV" + struct.pack(">ii", 13, 1) + b"\0"), "meta"),
        (zlib.compress(b"MSAV" + struct.pack(">ii", 13, 2) + b"\xff\xff"), "meta"),
        (zlib.compress(b"MSAV" + struct.pack(">ii", 13, 6) + b"\0\1\0\5xx"), "meta"),
    ],
)
def test_msav_rejects_bad_containers_and_metadata(data, code):
    with pytest.raises(InvalidMsav) as raised:
        validate_msav(data, "a.msav")
    assert raised.value.code == code


def test_msav_extension_and_compressed_size_limit():
    with pytest.raises(InvalidMsav, match="extension"):
        validate_msav(msav(), "a.zip")
    with pytest.raises(InvalidMsav, match="size"):
        validate_msav(msav(), "a.msav", max_file_bytes=2)


def test_msav_overinflating_tail_is_rejected_at_hard_cap():
    data = msav(tail=b"\0" * (MAX_INFLATED_BYTES + 1))
    with pytest.raises(InvalidMsav, match="inflation"):
        validate_msav(data, "a.msav")


def test_msav_inflation_boundary_and_custom_limit():
    data = msav(tail=b"\0" * 200)
    size = len(zlib.decompress(data))
    validate_msav(data, "a.msav", max_inflated_bytes=size)
    with pytest.raises(InvalidMsav, match="inflation"):
        validate_msav(data, "a.msav", max_inflated_bytes=size - 1)
    with pytest.raises(ValueError, match="limit"):
        validate_msav(data, "a.msav", max_inflated_bytes=MAX_INFLATED_BYTES + 1)


@pytest.mark.parametrize(
    "value",
    [
        b"\xf0\x9f\x90\x88",
        b"\xc1\x81",
        b"\xe0\x80\x81",
        b"\xe0",
        b"\x80",
        b"\xed\xa0\x80",
        b"\xed\xb0\x80",
    ],
)
def test_msav_rejects_invalid_modified_utf8(value):
    region = b"\0\1" + mutf8("name") + struct.pack(">H", len(value)) + value
    data = zlib.compress(b"MSAV" + struct.pack(">ii", 13, len(region)) + region)
    with pytest.raises(InvalidMsav, match="meta"):
        validate_msav(data, "a.msav")


@pytest.mark.parametrize("length", [-1, 1024 * 1024 + 1])
def test_msav_rejects_metadata_region_outside_hard_bound(length):
    data = zlib.compress(b"MSAV" + struct.pack(">ii", 13, length) + b"\0\0")
    with pytest.raises(InvalidMsav, match="meta"):
        validate_msav(data, "a.msav")


def test_msav_rejects_duplicate_metadata_and_unconsumed_region_bytes():
    region = struct.pack(">h", 2) + (mutf8("name") + mutf8("map")) * 2
    data = zlib.compress(b"MSAV" + struct.pack(">ii", 13, len(region)) + region)
    with pytest.raises(InvalidMsav, match="meta"):
        validate_msav(data, "a.msav")
    region = b"\0\0extra"
    data = zlib.compress(b"MSAV" + struct.pack(">ii", 13, len(region)) + region)
    with pytest.raises(InvalidMsav, match="meta"):
        validate_msav(data, "a.msav")


def region(body):
    return struct.pack(">i", len(body)) + body


def blob(data):
    return struct.pack(">i", len(data)) + data


def v13_assets(*assets):
    """(type, path, embedded data or None) per asset."""
    body = struct.pack(">ii", 0, len(assets))
    for kind, path, data in assets:
        body += struct.pack(">B", kind) + mutf8(path)
        body += b"\x01" + blob(data) if data is not None else b"\x00" + bytes(32)
    return region(body)


def test_data_patches_of_each_asset_format():
    patch = b'{"block": {"duo": {"health": 1}}}'
    assert data_patches(
        msav(
            tail=v13_assets(
                (0, "patch.json", patch),
                (1, "content/unit.hjson", None),
                (3, "image.png", b"png"),
            )
            + b"rest of the map"
        )
    ) == {"patch.json": patch.decode(), "content/unit.hjson": None}
    v12 = region(struct.pack(">ii", 0, 1) + blob(b"a") + struct.pack(">i", 0))
    assert data_patches(msav(version=12, tail=v12)) == {"patch-1": "a"}
    v11 = region(b"content header") + region(b"\x02" + blob(b"a") + blob(b"b"))
    assert data_patches(msav(version=11, tail=v11)) == {"patch-1": "a", "patch-2": "b"}
    assert data_patches(msav(tail=v13_assets((3, "image.png", b"png")))) == {}
    assert data_patches(msav(version=10)) == {}
    with pytest.raises(InvalidMsav):
        data_patches(msav(tail=v13_assets((0, "patch.json", b"x"))[:-1]))
    with pytest.raises(InvalidMsav):
        data_patches(msav(tail=region(struct.pack(">ii", 0, 1))))
