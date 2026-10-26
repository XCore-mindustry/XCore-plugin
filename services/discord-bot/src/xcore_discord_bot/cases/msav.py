"""Bounded MSAV container checks. Only the header and string metadata are decoded.

The remaining inflated bytes are counted and discarded, never deserialized. Gameplay
validation belongs to the server's detached inspection, not this reader.
``data_patches`` also reads the asset region, to show reviewers the patches as text.
"""

from __future__ import annotations

import hashlib
import struct
import zlib
from dataclasses import dataclass

MAX_INFLATED_BYTES = 64 * 1024 * 1024
MAX_META_BYTES = 1024 * 1024
CHUNK = 64 * 1024


class InvalidMsav(ValueError):
    def __init__(self, code: str) -> None:
        self.code = code
        super().__init__(code)


@dataclass(frozen=True, slots=True)
class MsavInfo:
    version: int
    meta: dict[str, str]
    sha256: str
    size: int


def _mutf8(raw: bytes) -> str:
    """Java DataInput.readUTF: UTF-16 code units encoded as modified UTF-8."""
    units = bytearray()
    pos = 0
    while pos < len(raw):
        first = raw[pos]
        pos += 1
        if 0 < first < 128:
            value = first
        elif 0xC0 <= first <= 0xDF:
            if pos >= len(raw) or raw[pos] & 0xC0 != 0x80:
                raise InvalidMsav("meta")
            value = (first & 31) << 6 | (raw[pos] & 63)
            pos += 1
            if value < 128 and not (first == 0xC0 and value == 0):
                raise InvalidMsav("meta")
        elif 0xE0 <= first <= 0xEF:
            if pos + 1 >= len(raw) or any(v & 0xC0 != 0x80 for v in raw[pos : pos + 2]):
                raise InvalidMsav("meta")
            value = (first & 15) << 12 | (raw[pos] & 63) << 6 | (raw[pos + 1] & 63)
            pos += 2
            if value < 2048:
                raise InvalidMsav("meta")
        else:
            raise InvalidMsav("meta")
        units.extend(struct.pack(">H", value))
    try:
        return units.decode("utf-16-be")
    except UnicodeDecodeError as error:
        raise InvalidMsav("meta") from error


def validate_msav(  # noqa: C901
    data: bytes,
    filename: str,
    *,
    max_file_bytes: int = 4 * 1024 * 1024,
    max_inflated_bytes: int = MAX_INFLATED_BYTES,
) -> MsavInfo:
    if not filename.lower().endswith(".msav"):
        raise InvalidMsav("extension")
    if not data or len(data) > max_file_bytes:
        raise InvalidMsav("size")
    if not 0 < max_inflated_bytes <= MAX_INFLATED_BYTES:
        raise ValueError("Invalid inflation limit")
    inflater = zlib.decompressobj()
    prefix = bytearray()
    inflated = 0
    try:
        for offset in range(0, len(data), CHUNK):
            pending = data[offset : offset + CHUNK]
            while pending:
                block = inflater.decompress(
                    pending, min(CHUNK, max_inflated_bytes - inflated + 1)
                )
                inflated += len(block)
                if inflated > max_inflated_bytes:
                    raise InvalidMsav("inflation")
                prefix.extend(block[: max(0, MAX_META_BYTES + 12 - len(prefix))])
                pending = inflater.unconsumed_tail
        if not inflater.eof:
            raise InvalidMsav("truncated")
        if inflater.unused_data:
            raise InvalidMsav("zlib")
    except zlib.error as error:
        raise InvalidMsav("zlib") from error
    if len(prefix) < 12:
        raise InvalidMsav("truncated")
    if prefix[:4] != b"MSAV":
        raise InvalidMsav("magic")
    version, length = struct.unpack_from(">ii", prefix, 4)
    if not 1 <= version <= 13:
        raise InvalidMsav("version")
    if not 2 <= length <= MAX_META_BYTES:
        raise InvalidMsav("meta")
    if len(prefix) < 12 + length:
        raise InvalidMsav("truncated")
    meta_bytes = memoryview(prefix)[12 : 12 + length]
    count = struct.unpack_from(">h", meta_bytes)[0]
    if count < 0:
        raise InvalidMsav("meta")
    pos = 2

    def string() -> str:
        nonlocal pos
        if pos + 2 > length:
            raise InvalidMsav("meta")
        size = struct.unpack_from(">H", meta_bytes, pos)[0]
        pos += 2
        if pos + size > length:
            raise InvalidMsav("meta")
        value = _mutf8(bytes(meta_bytes[pos : pos + size]))
        pos += size
        return value

    meta = {}
    for _ in range(count):
        key, value = string(), string()
        if key in meta:
            raise InvalidMsav("meta")
        meta[key] = value
    if pos != length:
        raise InvalidMsav("meta")
    return MsavInfo(version, meta, hashlib.sha256(data).hexdigest(), len(data))


def data_patches(data: bytes) -> dict[str, str | None]:  # noqa: C901
    """Gameplay patch and content assets of a validated map, path -> text.

    Only the regions up to the assets are inflated. An asset saved as a hash only
    (not embedded in the file) maps to ``None``.
    """
    inflater = zlib.decompressobj()
    buf = bytearray()
    source = data

    def need(size: int) -> None:
        nonlocal source
        if size > MAX_INFLATED_BYTES:
            raise InvalidMsav("inflation")
        while len(buf) < size:
            block = inflater.decompress(source, size - len(buf))
            source = inflater.unconsumed_tail
            if not block and not source:
                raise InvalidMsav("truncated")
            buf.extend(block)

    def region(start: int) -> tuple[int, int]:
        need(start + 4)
        (length,) = struct.unpack_from(">i", buf, start)
        if length < 0:
            raise InvalidMsav("assets")
        need(start + 4 + length)
        return start + 4, start + 4 + length

    try:
        need(8)
        version = struct.unpack_from(">i", buf, 4)[0]
        if version < 11:
            return {}
        _, end = region(8)  # metadata
        if version == 11:
            _, end = region(end)  # content header
        start, end = region(end)
        r = memoryview(buf)[start:end]
        pos = 0

        def take(size: int) -> bytes:
            nonlocal pos
            if pos + size > len(r):
                raise InvalidMsav("assets")
            pos += size
            return bytes(r[pos - size : pos])

        def number(fmt: str) -> int:
            return struct.unpack(fmt, take(struct.calcsize(fmt)))[0]

        def text() -> str:
            return take(number(">i")).decode("utf-8", "replace")

        if version == 11:
            return {f"patch-{i + 1}": text() for i in range(number(">B"))}
        number(">i")  # asset format revision
        count = number(">i")
        if version == 12:
            return {f"patch-{i + 1}": text() for i in range(count)}
        found: dict[str, str | None] = {}
        for _ in range(count):
            kind = number(">B")
            path = _mutf8(take(number(">H")))
            if number(">B"):  # embedded
                value = text()
            else:
                take(32)  # hash of a file kept outside the map
                value = None
            if kind <= 1:  # patch, content
                found[path] = value
        return found
    except (struct.error, zlib.error) as error:
        raise InvalidMsav("assets") from error
