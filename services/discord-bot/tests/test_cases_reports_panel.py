"""B5: reports from the Discord panel (9.2) and the private thread of every Discord reporter."""

from __future__ import annotations

import re
from datetime import timedelta
from types import SimpleNamespace

import pytest
from cases_fakes import member
from cases_reports_fakes import (
    REPORTER_UUID,
    STAFF,
    TARGET_UUID,
    act,
    open_report,
    panel,
    reports_core,
)

from xcore_discord_bot.cases.flows import reports
from xcore_discord_bot.cases.model import AccessDenied, CaseStatus
from xcore_discord_bot.cases.reconcile import reconcile_cases
from xcore_discord_bot.i18n import t
from xcore_discord_bot.mongo_store import EXACT_NAME_CANDIDATES, MongoStore


def private_threads(bot):
    return [thread for thread in bot.channels[100].all_threads if thread.private]


def reply(interaction):
    [text] = interaction.followup.texts()
    return text


def card_reporters(bot, case):
    card = bot.channels[int(case.discord["thread_id"])].messages[0].embeds[0]
    count = len(case.reporters)
    return next(
        field.value
        for field in card.fields
        if field.name == t("en", "reports-card-reporters", count=count)
    )


class Attachment:
    id, filename, size = 9, "proof.png", 10

    async def to_file(self):
        return "file"


# --- who is reported ----------------------------------------------------------------------------


@pytest.mark.parametrize("who", ["-5", "#-5", "target", " TARGET "])
async def test_the_reported_player_is_found_by_id_or_exact_name(who):
    service, _, bot = await reports_core()
    _, case = await panel(service, bot, {"who": who, "what": "grief"})
    assert case.subject.player_uuid == TARGET_UUID
    assert case.dedup_key == f"report:{TARGET_UUID}"
    assert reports.UNRESOLVED not in case.flags


async def test_a_name_with_colour_tags_is_found_by_its_plain_text():
    service, _, bot = await reports_core()
    _, case = await panel(service, bot, {"who": "Reporter", "what": "grief"})
    assert case.subject.player_uuid == REPORTER_UUID


@pytest.mark.parametrize("who", ["Nobody Known", "Targ", "Twin", "99999"])
async def test_an_unknown_or_ambiguous_player_leaves_the_subject_for_staff(who):
    service, _, bot = await reports_core()
    players = service.player_store.database["players"].docs
    players += [
        {"_id": 10, "uuid": "twin-1", "pid": 11, "nickname": "[blue]Twin"},
        {"_id": 11, "uuid": "twin-2", "pid": 12, "custom_nickname": "twin"},
    ]
    _, case = await panel(service, bot, {"who": who, "what": "grief"})
    assert case.subject is None and case.dedup_key is None
    assert reports.UNRESOLVED in case.flags
    assert case.payload["raw_subject"] == who
    service.transport.rpc_case_evidence.assert_not_awaited()


class _RegexPlayers:
    """Just enough of a Mongo collection to run the name query."""

    def __init__(self, rows):
        self.rows, self.limits = rows, []

    def find(self, query):
        def hit(row):
            for option in query["$or"]:
                [(field, condition)] = option.items()
                flags = re.IGNORECASE if "i" in condition["$options"] else 0
                if re.search(condition["$regex"], str(row.get(field) or ""), flags):
                    return True
            return False

        found = [row for row in self.rows if hit(row)]
        collection = self

        class Cursor:
            def limit(self, count):
                collection.limits.append(count)
                self.rows = found[:count]
                return self

            def __aiter__(self):
                async def rows():
                    for row in self.rows:
                        yield row

                return rows()

        return Cursor()


@pytest.mark.parametrize(
    ("name", "found"),
    [
        ("bob", ["u-bob"]),
        ("Bo", []),
        ("a.c", []),
        ("Carl", []),
        ("[VIP]Carl", ["u-carl"]),
        ("Dup", ["u-dup-1", "u-dup-2"]),
        ("ghost", ["u-ghost"]),
        ("", []),
    ],
)
async def test_the_exact_name_query_ignores_case_and_colour_tags_only(name, found):
    rows = [
        {"uuid": "u-bob", "pid": 1, "nickname": "[red]B[]ob"},
        {"uuid": "u-abc", "pid": 2, "nickname": "abc"},
        # A bracketed group that is not a colour tag is part of the name.
        {"uuid": "u-carl", "pid": 3, "nickname": "[VIP]Carl"},
        {"uuid": "u-dup-1", "pid": 4, "nickname": "[#00ff00]Dup"},
        {"uuid": "u-dup-2", "pid": 5, "username": "dup"},
        {"uuid": "u-dup-3", "pid": 6, "nickname": "Dup"},
        {
            "uuid": "u-ghost",
            "pid": 7,
            "nickname": "x",
            "custom_nickname": "[gold]Ghost",
        },
    ]
    collection = _RegexPlayers(rows)
    store = MongoStore(SimpleNamespace())
    store._db = {"players": collection}
    players = await store.find_players_by_exact_name(name)
    assert [player.uuid for player in players] == found
    assert collection.limits in ([], [EXACT_NAME_CANDIDATES])


# --- the reporter's side ------------------------------------------------------------------------


async def test_a_panel_report_opens_a_private_thread_and_replies_with_the_number():
    service, db, bot = await reports_core()
    service.transport.rpc_case_evidence.return_value = {
        "server": "survival",
        "targetOnline": True,
        "chat": [{"sentAt": 0, "playerName": "Target", "role": "target", "text": "hi"}],
    }
    interaction, case = await panel(
        service, bot, {"who": "-5", "what": "griefed the core", "when": "now"}
    )
    assert case.origin.source == "discord" and case.opener.discord_id == "301"
    assert case.payload == {"comment": "griefed the core", "when": "now"}
    [thread] = private_threads(bot)
    assert case.user_threads == {"301": str(thread.id)}
    assert thread.members == {301}
    assert reply(interaction) == "{}\n<#{}>".format(
        t("en", "cases-registered", number=case.number), thread.id
    )
    # The player is online: what the server has now is attached.
    service.transport.rpc_case_evidence.assert_awaited_once_with(
        server="survival", player_uuid=TARGET_UUID
    )
    assert case.evidence["chat"][0]["text"] == "hi"
    # A member hears in Discord: there is no in-game acknowledgement.
    assert db["case_notices"].docs == []
    assert f"<#{thread.id}>" in card_reporters(bot, case)


async def test_files_from_the_form_are_copied_into_the_case_post():
    service, _, bot = await reports_core()
    interaction, case = await panel(
        service, bot, {"who": "-5", "what": "grief"}, files=[Attachment()]
    )
    case = await service.get(case.number)
    [attachment] = case.evidence["attachments"]
    assert attachment["file_name"] == "proof.png"
    post = bot.channels[int(case.discord["thread_id"])]
    assert any(m.id == int(attachment["message_id"]) for m in post.messages)
    assert t("en", "cases-files-retry") not in reply(interaction)


# --- gap 3: a Discord reporter who joins someone else's report ---------------------------------


async def test_a_discord_reporter_joining_a_game_report_gets_a_thread_of_their_own():
    service, db, bot = await reports_core()
    case = await open_report(service)
    await act(service, case, "take")
    interaction, merged = await panel(service, bot, {"who": "-5", "what": "me too"})
    assert merged.number == case.number and len(db["cases"].docs) == 1
    # The report stays the game reporter's: origin and opener are unchanged.
    assert merged.origin == case.origin and merged.opener == case.opener
    [joined] = merged.co_openers
    assert joined["source"] == "discord" and joined["discord_id"] == "301"
    [thread] = private_threads(bot)
    assert merged.user_threads == {"301": str(thread.id)}
    assert (await service.store.by_thread(thread.id)).number == case.number
    # Theirs alone, with the staff member who took the case; not the opener's.
    assert thread.members == {301, STAFF}
    assert reply(interaction).endswith(f"<#{thread.id}>")
    reporters = card_reporters(bot, merged).splitlines()
    assert reporters[0].endswith("<@201>")
    assert reporters[1].endswith(f"<#{thread.id}>")
    assert service.creation_complete(merged)


async def test_staff_who_take_the_case_later_join_every_reporter_thread():
    service, _, bot = await reports_core()
    case = await open_report(service)
    await panel(service, bot, {"who": "-5", "what": "me too"})
    await act(service, case, "take")
    [thread] = private_threads(bot)
    assert thread.members == {301, STAFF}


async def test_the_outcome_reaches_each_reporter_in_their_own_thread_only():
    service, db, bot = await reports_core()
    case = await open_report(service)
    await panel(service, bot, {"who": "-5", "what": "me too"})
    [thread] = private_threads(bot)
    before = len(thread.messages)
    closed, _ = await act(
        service,
        case,
        "reject",
        values={"reason_code": "no_evidence", "reason_text": "x"},
    )
    assert closed.status == CaseStatus.REJECTED
    text = t("en", "reports-outcome-no-evidence", number=case.number)
    # The Discord reporter: in their thread (then archived and locked) and by DM.
    assert [m.content for m in thread.messages[before:]] == [text]
    assert thread.archived and thread.locked
    assert bot.users[301].messages == [text]
    # The game reporter: in game and by DM in their language, never in someone's thread.
    assert bot.users[201].messages == [
        t("uk", "reports-outcome-no-evidence", number=case.number)
    ]
    assert {row["player_uuid"] for row in db["case_notices"].docs} == {REPORTER_UUID}
    assert len(private_threads(bot)) == 1


async def test_reporting_again_from_discord_keeps_one_thread():
    service, _, bot = await reports_core()
    case = await open_report(service)
    await panel(service, bot, {"who": "-5", "what": "me too"}, id=700)
    _, again = await panel(service, bot, {"who": "-5", "what": "still"}, id=710)
    assert again.number == case.number and len(again.co_openers) == 2
    assert len(private_threads(bot)) == 1 and len(again.user_threads) == 1
    assert [p.discord_id for p in again.reporters] == ["201", "301"]


async def test_the_opener_reporting_again_from_discord_keeps_their_thread():
    service, _, bot = await reports_core()
    _, case = await panel(service, bot, {"who": "-5", "what": "grief"}, id=700)
    _, again = await panel(service, bot, {"who": "-5", "what": "again"}, id=710)
    assert again.number == case.number
    assert len(private_threads(bot)) == 1
    assert again.user_threads == case.user_threads


@pytest.mark.parametrize("step", ["user_create", "user_add", "user_send"])
async def test_a_failed_reporter_thread_is_finished_by_the_next_attempt(step):
    service, _, bot = await reports_core()
    case = await open_report(service)
    bot.failures.arm(step, after=True)
    interaction, merged = await panel(service, bot, {"who": "-5", "what": "me too"})
    # The case exists and the reporter has its number whatever failed.
    assert t("en", "cases-registered", number=case.number) in reply(interaction)
    assert not service.creation_complete(merged)
    await service.resume_creation(case.number)
    current = await service.get(case.number)
    assert service.creation_complete(current)
    [thread] = private_threads(bot)
    assert current.user_threads == {"301": str(thread.id)}
    assert 301 in thread.members
    note = t("en", "cases-user-thread-note", number=case.number)
    assert [m.content for m in thread.messages].count(note) == 1


async def test_a_deleted_reporter_thread_is_made_again():
    service, _, bot = await reports_core()
    case = await open_report(service)
    await panel(service, bot, {"who": "-5", "what": "me too"})
    [old] = private_threads(bot)
    bot.channels[100].all_threads.remove(old)
    await service.thread_deleted(old.id, 100)
    # The repair task notices the missing thread.
    await reconcile_cases(service)
    current = await service.get(case.number)
    [new] = private_threads(bot)
    assert new.id != old.id and current.user_threads == {"301": str(new.id)}


async def test_only_the_owner_of_a_thread_is_announced_to_staff():
    service, _, bot = await reports_core()
    case = await open_report(service)
    # A real-length id: mention escaping only matches 17-20 digits.
    owner = 123456789012345678
    await panel(service, bot, {"who": "-5", "what": "me too"}, user=member(owner, ()))
    [thread] = private_threads(bot)
    post = bot.channels[int(case.discord["thread_id"])]

    def message(author):
        return SimpleNamespace(
            author=SimpleNamespace(id=author, bot=False),
            channel=thread,
            jump_url=f"https://discord.test/{author}",
        )

    before = len(post.messages)
    await service.threads.announce_user_message(service, message(STAFF))
    assert len(post.messages) == before
    await service.threads.announce_user_message(service, message(owner))
    assert post.messages[-1].content == t(
        "en",
        "cases-user-message",
        name=f"Staff {owner} · <@{owner}>",
        url=f"https://discord.test/{owner}",
    )
    # A second message in a row is covered by the same notice, a later one gets its own.
    await service.threads.announce_user_message(service, message(owner))
    assert len(post.messages) == before + 1
    post.messages[-1].created_at -= timedelta(minutes=31)
    await service.threads.announce_user_message(service, message(owner))
    assert len(post.messages) == before + 2


async def test_the_conflict_rule_covers_a_discord_reporter():
    service, _, bot = await reports_core()
    case = await open_report(service)
    await panel(service, bot, {"who": "-5", "what": "me too"})
    with pytest.raises(AccessDenied):
        await act(service, case, "take", user=member(301, (10,)))
