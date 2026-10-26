from __future__ import annotations

import logging

from .bus.transport import RedisTransport

logger = logging.getLogger(__name__)


class RedisBus(RedisTransport):
    """The transport under its old name, until its callers move to RedisTransport."""
