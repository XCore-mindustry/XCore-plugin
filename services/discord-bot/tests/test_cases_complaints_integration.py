"""B9 subject exclusion must also run through B5's completed recovery shortcuts."""

import pytest
from cases_complaints_fakes import act, complaint_core, opened
from cases_fakes import member

from xcore_discord_bot.cases.model import Party


@pytest.mark.parametrize("closed", [False, True])
@pytest.mark.parametrize("subject_id", ["42", "99"])
async def test_completed_recovery_removes_subject_membership(closed, subject_id):
    service, _, bot = await complaint_core()
    case = await opened(service, bot)
    case = await service.set_subject(
        case.number, member(101, (20,)), Party(discord_id=subject_id)
    )
    if closed:
        case = await act(service, case, "resolve", values={"note": "Reviewed"})
        assert case.progress["closed_done"]
    else:
        assert service.creation_complete(case)
    receipts = case.progress.get("notification_receipts", [])
    thread = bot.channels[int(case.discord["user_thread_id"])]
    # An explicit invitation needs removal even when every projection receipt is complete.
    thread.members.add(int(subject_id))
    if closed:
        await service.finish_close(case.number)
    else:
        await service.resume_creation(case.number)
    current = await service.get(case.number)
    assert int(subject_id) not in thread.members
    assert thread.archived == closed and thread.locked == closed
    assert current.progress.get("notification_receipts", []) == receipts
    assert int(subject_id) not in bot.users
