"""Player PIDs are signed.

Technical admins hand out zero and negative PIDs to special players (for example event
participants), so no ordinary value can mean "no PID". ``NO_PID`` is the one reserved value,
the same one the game servers use (``PlayerPids.NONE`` in XCore-plugin).
"""

from __future__ import annotations

NO_PID = -(2**31)


def is_assigned(pid: int | None) -> bool:
    return pid is not None and pid != NO_PID


def or_none(pid: int | None) -> int | None:
    """The PID, or ``None`` when it is not assigned."""
    return pid if is_assigned(pid) else None
