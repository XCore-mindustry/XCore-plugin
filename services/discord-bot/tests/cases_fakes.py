from __future__ import annotations

import re
from copy import deepcopy
from datetime import UTC, datetime
from types import SimpleNamespace

import discord
from bson import ObjectId
from pymongo import ReturnDocument
from pymongo.errors import DuplicateKeyError

from xcore_discord_bot.cases.notify import CaseNotifier
from xcore_discord_bot.cases.registry import FlowRegistry
from xcore_discord_bot.cases.service import CaseService
from xcore_discord_bot.cases.store import CaseStore
from xcore_discord_bot.cases.threads import FORUM_TAGS, CaseThreads
from xcore_discord_bot.settings import Settings

NOW = datetime(2026, 10, 13, 12, tzinfo=UTC)
MISSING = object()


def values(doc, path):
    if not path:
        return [doc]
    if isinstance(doc, list):
        if path[0].isdigit():
            index = int(path[0])
            return values(doc[index], path[1:]) if index < len(doc) else [MISSING]
        return [v for item in doc for v in values(item, path)]
    if not isinstance(doc, dict) or path[0] not in doc:
        return [MISSING]
    return values(doc[path[0]], path[1:])


def get(doc, key, default=None):
    found = values(doc, key.split("."))[0]
    return default if found is MISSING else found


def matches(doc, query):
    for key, want in query.items():
        if key == "$expr":
            if not evaluate(want, doc):
                return False
            continue
        if key == "$or":
            if not any(matches(doc, option) for option in want):
                return False
            continue
        if key == "$and":
            if not all(matches(doc, option) for option in want):
                return False
            continue
        have = values(doc, key.split("."))
        if isinstance(want, dict):
            for op, target in want.items():
                if op == "$exists":
                    ok = any(v is not MISSING for v in have) == target
                elif op == "$type":
                    kind = {"string": str, "int": int}[target]
                    ok = any(type(v) is kind for v in have)
                elif op == "$ne":
                    ok = all(v != target for v in have)
                elif op == "$in":
                    ok = any(v in target for v in have)
                elif op == "$regex":
                    flags = re.IGNORECASE if "i" in want.get("$options", "") else 0
                    ok = any(
                        isinstance(v, str) and re.search(target, v, flags) for v in have
                    )
                elif op == "$options":
                    continue
                elif op in {"$gt", "$gte", "$lt", "$lte"}:

                    def compare(v, op=op, target=target):
                        if v is MISSING or v is None:
                            return False
                        return {
                            "$gt": lambda: v > target,
                            "$gte": lambda: v >= target,
                            "$lt": lambda: v < target,
                            "$lte": lambda: v <= target,
                        }[op]()

                    ok = any(compare(v) for v in have)
                else:
                    raise AssertionError(f"Unsupported fake query {op}")
                if not ok:
                    return False
        elif not any(v == want or v is MISSING and want is None for v in have):
            return False
    return True


def evaluate(expr, doc, variables=None):
    """An aggregation expression, for the operators of the bot's pipeline updates."""
    variables = variables or {}
    if isinstance(expr, str) and expr.startswith("$$"):
        return variables[expr[2:]]
    if isinstance(expr, str) and expr.startswith("$"):
        return get(doc, expr[1:])
    if isinstance(expr, list):
        return [evaluate(item, doc, variables) for item in expr]
    if not isinstance(expr, dict):
        return expr
    ((op, arg),) = expr.items()
    if op == "$filter":
        name = arg["as"]
        return [
            item
            for item in evaluate(arg["input"], doc, variables)
            if truthy(evaluate(arg["cond"], doc, {**variables, name: item}))
        ]
    if op == "$regexMatch":
        found = evaluate(arg["input"], doc, variables)
        return re.search(evaluate(arg["regex"], doc, variables), found) is not None
    values = evaluate(arg, doc, variables)
    if op == "$toString":
        return None if values is None else str(values)
    if op == "$ifNull":
        return values[0] if values[0] is not None else values[1]
    if op == "$eq":
        return values[0] == values[1]
    if op == "$ne":
        return values[0] != values[1]
    if op == "$cond":
        return values[1] if truthy(values[0]) else values[2]
    if op == "$and":
        return all(truthy(value) for value in values)
    if op in COMPARISONS:
        return COMPARISONS[op](order(values[0]), order(values[1]))
    raise AssertionError(f"Unsupported fake expression {op}")


COMPARISONS = {
    "$lt": lambda a, b: a < b,
    "$lte": lambda a, b: a <= b,
    "$gt": lambda a, b: a > b,
    "$gte": lambda a, b: a >= b,
}


def truthy(value):
    """Aggregation truth: only null, false and 0 are false; "" and [] are true."""
    return value is not None and value is not False and value != 0


def order(value):
    """BSON order for the values the bot compares: null before every number."""
    if value is None:
        return (0, 0)
    assert isinstance(value, int | float) and not isinstance(value, bool), value
    return (1, value)


def numbers(found):
    return [v for v in found if isinstance(v, int | float) and not isinstance(v, bool)]


def accumulate(op, found):
    """A ``$group`` accumulator over one evaluated value per grouped document."""
    if op == "$sum":
        return sum(numbers(found))
    if op == "$avg":
        found = numbers(found)
        return sum(found) / len(found) if found else None
    if op in ("$max", "$min"):
        found = [v for v in found if v is not None]
        return (max if op == "$max" else min)(found, key=order) if found else None
    raise AssertionError(f"Unsupported fake accumulator {op}")


def unwind(docs, path):
    """``$unwind`` without options: one document per array item; no item, no document."""
    key = path.removeprefix("$")
    for doc in docs:
        items = get(doc, key)
        for item in items if isinstance(items, list) else []:
            copy = deepcopy(doc)
            assign(copy, key, item)
            yield copy


def group(docs, spec):
    groups: dict = {}
    for doc in docs:
        key = evaluate(spec["_id"], doc)
        groups.setdefault(repr(key), (key, []))[1].append(doc)
    rows = []
    for key, members in groups.values():
        row = {"_id": key}
        for name, accumulator in spec.items():
            if name != "_id":
                ((op, expr),) = accumulator.items()
                row[name] = accumulate(op, [evaluate(expr, doc) for doc in members])
        rows.append(row)
    return rows


def assign(doc, key, value):
    parts = key.split(".")
    target = doc
    for part in parts[:-1]:
        target = target.setdefault(part, {})
    target[parts[-1]] = deepcopy(value)


def assign_filtered(doc, parts, value, array_filters):
    """Set ``value`` at a path whose ``$[name]`` steps pick array items by ``array_filters``."""
    head, rest = parts[0], parts[1:]
    if head.startswith("$[") and head.endswith("]"):
        prefix = head[2:-1] + "."
        condition = {
            key.removeprefix(prefix): want
            for array_filter in array_filters
            for key, want in array_filter.items()
            if key.startswith(prefix)
        }
        for item in doc:
            if matches(item, condition):
                assign_filtered(item, rest, value, array_filters)
    elif rest:
        assign_filtered(doc.setdefault(head, {}), rest, value, array_filters)
    else:
        doc[head] = deepcopy(value)


def project(doc, projection):
    """An inclusion projection, which is the only kind the bot sends."""
    assert all(include in (0, 1) for include in projection.values()), projection
    kept = {key for key, include in projection.items() if include}
    if projection.get("_id", 1):
        kept.add("_id")
    return {key: value for key, value in doc.items() if key in kept}


def unset(doc, key):
    parts = key.split(".")
    target = doc
    for part in parts[:-1]:
        target = target.get(part, {})
    target.pop(parts[-1], None)


class Cursor:
    def __init__(self, docs):
        self.docs = deepcopy(docs)

    def sort(self, key, direction=None):
        fields = key if isinstance(key, list) else [(key, direction)]
        for name, order in reversed(fields):
            self.docs.sort(
                key=lambda doc: (get(doc, name) is not None, get(doc, name)),
                reverse=order == -1,
            )
        return self

    def skip(self, count):
        self.docs = self.docs[count:]
        return self

    def limit(self, count):
        if count:
            self.docs = self.docs[:count]
        return self

    async def to_list(self, length=None):
        return deepcopy(self.docs[:length] if length is not None else self.docs)

    def __aiter__(self):
        async def generate():
            for doc in self.docs:
                yield deepcopy(doc)

        return generate()


class Collection:
    def __init__(self, db=None):
        self.db = db
        self.docs = []
        self.indexes = []

    def _join(self, session):
        # A real server would apply such a call outside the transaction, so a rollback
        # would not undo it.
        open_transaction = self.db.transaction if self.db is not None else None
        if open_transaction is not None and session is not open_transaction:
            raise AssertionError("Call outside the open transaction")

    def find(self, query, projection=None):
        found = [doc for doc in self.docs if matches(doc, query)]
        if projection:
            found = [project(doc, projection) for doc in found]
        return Cursor(found)

    async def aggregate(self, pipeline):
        cursor = Cursor(self.docs)
        for stage in pipeline:
            ((name, arg),) = stage.items()
            if name == "$match":
                cursor.docs = [doc for doc in cursor.docs if matches(doc, arg)]
            elif name == "$sort":
                cursor.sort(list(arg.items()))
            elif name == "$skip":
                cursor.skip(arg)
            elif name == "$limit":
                cursor.limit(arg)
            elif name == "$project":
                cursor.docs = [project(doc, arg) for doc in cursor.docs]
            elif name == "$unwind":
                cursor.docs = list(unwind(cursor.docs, arg))
            elif name == "$group":
                cursor.docs = group(cursor.docs, arg)
            else:
                raise AssertionError(f"Unsupported fake stage {name}")
        return cursor

    async def find_one(self, query, *, session=None):
        self._join(session)
        return next((deepcopy(doc) for doc in self.docs if matches(doc, query)), None)

    async def count_documents(self, query):
        return sum(matches(doc, query) for doc in self.docs)

    async def create_index(self, keys, **options):
        self.indexes.append((keys, options))
        for doc in self.docs:
            self._unique(doc, ignore=doc)

    def _unique(self, doc, ignore=None):
        for keys, options in [("_id", {"unique": True}), *self.indexes]:
            if not options.get("unique"):
                continue
            partial = options.get("partialFilterExpression", {})
            if not matches(doc, partial):
                continue
            names = [keys] if isinstance(keys, str) else [k for k, _ in keys]
            if options.get("sparse") and all(
                get(doc, name, MISSING) is MISSING for name in names
            ):
                continue
            for other in self.docs:
                if other is ignore or not matches(other, partial):
                    continue
                if options.get("sparse") and all(
                    get(other, name, MISSING) is MISSING for name in names
                ):
                    continue
                if all(get(other, name) == get(doc, name) for name in names):
                    raise DuplicateKeyError(f"Duplicate {keys}")

    async def insert_one(self, doc, *, session=None):
        self._join(session)
        # Like the driver, which also adds the generated id to the caller's dict.
        doc.setdefault("_id", ObjectId())
        self._unique(doc)
        self.docs.append(deepcopy(doc))

    async def replace_one(self, query, replacement, *, upsert=False):
        target = next((doc for doc in self.docs if matches(doc, query)), None)
        if target is None and not upsert:
            return SimpleNamespace(matched_count=0, modified_count=0, upserted_id=None)
        candidate = deepcopy(replacement)
        candidate["_id"] = target["_id"] if target is not None else ObjectId()
        self._unique(candidate, ignore=target)
        if target is None:
            self.docs.append(candidate)
            return SimpleNamespace(
                matched_count=0, modified_count=0, upserted_id=candidate["_id"]
            )
        modified = int(candidate != target)
        target.clear()
        target.update(candidate)
        return SimpleNamespace(
            matched_count=1, modified_count=modified, upserted_id=None
        )

    async def delete_one(self, query):
        target = next((doc for doc in self.docs if matches(doc, query)), None)
        if target is not None:
            self.docs.remove(target)
        return SimpleNamespace(deleted_count=int(target is not None))

    async def delete_many(self, query):
        before = len(self.docs)
        self.docs = [doc for doc in self.docs if not matches(doc, query)]
        return SimpleNamespace(deleted_count=before - len(self.docs))

    async def find_one_and_update(
        self,
        query,
        update,
        *,
        upsert=False,
        return_document=ReturnDocument.BEFORE,
        session=None,
    ):
        self._join(session)
        target = next((doc for doc in self.docs if matches(doc, query)), None)
        if target is None and not upsert:
            return None
        before = deepcopy(target)
        candidate = deepcopy(target) if target is not None else {}
        if target is None:
            for key, value in query.items():
                if not key.startswith("$") and not isinstance(value, dict):
                    assign(candidate, key, value)
            for key, value in update.get("$setOnInsert", {}).items():
                assign(candidate, key, value)
        if isinstance(update, list):
            # An update pipeline; each stage sees the document the previous one left.
            for stage in update:
                assert set(stage) == {"$set"}, f"Unsupported fake stage {stage}"
                values = {k: evaluate(v, candidate) for k, v in stage["$set"].items()}
                for key, value in values.items():
                    assign(candidate, key, value)
            update = {}
        for key, value in update.get("$set", {}).items():
            assign(candidate, key, value)
        for key, value in update.get("$inc", {}).items():
            assign(candidate, key, get(candidate, key, 0) + value)
        for key, value in update.get("$addToSet", {}).items():
            items = deepcopy(get(candidate, key, []))
            if value not in items:
                items.append(value)
            assign(candidate, key, items)
        for key in update.get("$unset", {}):
            unset(candidate, key)
        for key, value in update.get("$push", {}).items():
            items = deepcopy(get(candidate, key, []))
            items.extend(
                value["$each"]
                if isinstance(value, dict) and "$each" in value
                else [value]
            )
            assign(candidate, key, items)
        self._unique(candidate, ignore=target)
        if target is None:
            self.docs.append(candidate)
        else:
            target.clear()
            target.update(candidate)
        return (
            deepcopy(candidate) if return_document == ReturnDocument.AFTER else before
        )

    async def update_one(self, query, update, *, upsert=False, session=None):
        before = await self.find_one(query, session=session)
        after = await self.find_one_and_update(
            query,
            update,
            upsert=upsert,
            return_document=ReturnDocument.AFTER,
            session=session,
        )
        return SimpleNamespace(
            modified_count=int(before != after), matched_count=int(before is not None)
        )

    async def update_many(self, query, update, *, array_filters=(), session=None):
        self._join(session)
        matched = modified = 0
        for doc in self.docs:
            if not matches(doc, query):
                continue
            before = deepcopy(doc)
            for key, value in update.get("$set", {}).items():
                assign_filtered(doc, key.split("."), value, array_filters)
            matched += 1
            modified += before != doc
        return SimpleNamespace(modified_count=modified, matched_count=matched)


class Session:
    """A client session whose transaction undoes every write when its callback raises."""

    def __init__(self, db):
        self.db = db

    async def __aenter__(self):
        return self

    async def __aexit__(self, *exc_info):
        return None

    async def with_transaction(self, callback):
        assert self.db.transaction is None, "Transactions do not nest"
        snapshot = {name: deepcopy(c.docs) for name, c in self.db.collections.items()}
        self.db.transaction = self
        try:
            return await callback(self)
        except BaseException:
            for name, collection in self.db.collections.items():
                collection.docs = snapshot.get(name, [])
            raise
        finally:
            self.db.transaction = None


class Client:
    def __init__(self, db):
        self.db = db

    def start_session(self):
        return Session(self.db)


class Database:
    def __init__(self):
        self.collections = {}
        self.client = Client(self)
        self.transaction = None

    def __getitem__(self, name):
        return self.collections.setdefault(name, Collection(self))

    async def command(self, name):
        assert name == "ping"
        return {"ok": 1}


class Failures:
    def __init__(self):
        self.pending = {}

    def arm(self, name, *, after=False):
        self.pending[(name, after)] = True

    def hit(self, name, *, after=False):
        if self.pending.pop((name, after), False):
            raise RuntimeError(f"Injected {name} failure")


def member(discord_id=101, roles=(10,)):
    return SimpleNamespace(
        id=discord_id,
        display_name=f"Staff {discord_id}",
        bot=False,
        roles=[SimpleNamespace(id=r) for r in roles],
    )


class Role:
    def __init__(self, id, bot=False, admin=False):
        self.id, self.bot = id, bot
        self.permissions = discord.Permissions(administrator=admin)

    def is_bot_managed(self):
        return self.bot


class Guild:
    def __init__(self):
        self.id = 123
        self.roles = [Role(n) for n in (1, 10, 20, 30, 40)]
        self.default_role = self.roles[0]
        self.me = member(999, ())
        self.members = {101: member(101), 102: member(102)}

    def get_role(self, id):
        return next((role for role in self.roles if role.id == id), None)

    def get_member(self, id):
        return self.members.get(id)

    async def fetch_member(self, id):
        raise discord.NotFound(
            SimpleNamespace(status=404, reason="Not Found"), "Unknown Member"
        )


class Message:
    def __init__(self, bot, channel, id, content="", embed=None, view=None):
        self.bot, self.channel, self.id = bot, channel, id
        self.author = bot.user
        self.created_at = discord.utils.utcnow()
        self.content = content
        self.embeds = [embed] if embed else []
        self.view = view
        self.components = []
        self.edits = []

    async def edit(self, **changes):
        label = (
            "panel_edit"
            if self.channel.type == discord.ChannelType.text
            else "card_edit"
        )
        self.bot.failures.hit(label)
        self.bot.calls.append((label, self.id))
        self.edits.append(changes)
        self.content = changes.get("content", self.content)
        if "embed" in changes:
            self.embeds = [changes["embed"]]
        self.embeds = changes.get("embeds", self.embeds)
        self.view = changes.get("view", self.view)
        self.bot.failures.hit(label, after=True)
        return self


class TextChannel:
    type = discord.ChannelType.text

    def __init__(self, bot, id, guild):
        self.bot, self.id, self.guild = bot, id, guild
        self.messages, self.all_threads = [], []
        self.role_permissions = {}

    @property
    def threads(self):
        return [thread for thread in self.all_threads if not thread.archived]

    def permissions_for(self, who):
        if who.id == 999:
            return discord.Permissions.all()
        return self.role_permissions.get(
            who.id,
            discord.Permissions(view_channel=True, send_messages_in_threads=True),
        )

    def history(self, *, limit=None, oldest_first=False, after=None):
        messages = self.messages if oldest_first else list(reversed(self.messages))
        if after is not None:
            messages = [message for message in messages if message.id > after.id]
        return CursorObjects(messages[:limit] if limit is not None else messages)

    async def fetch_message(self, id):
        self.bot.failures.hit("post_fetch")
        result = next((m for m in self.messages if m.id == id), None)
        if result is None:
            raise discord.NotFound(
                SimpleNamespace(status=404, reason="Not Found"), "Unknown Message"
            )
        self.bot.failures.hit("post_fetch", after=True)
        return result

    async def send(self, content="", **kwargs):
        self.bot.failures.hit("panel_send")
        message = Message(
            self.bot,
            self,
            self.bot.next_id(),
            content,
            kwargs.get("embed"),
            kwargs.get("view"),
        )
        message.embeds = kwargs.get("embeds", message.embeds)
        self.messages.append(message)
        self.bot.failures.hit("panel_send", after=True)
        return message

    def archived_threads(self, **kwargs):
        self.bot.calls.append(("archived_threads", self.id))
        return CursorObjects([t for t in self.all_threads if t.archived])

    async def create_thread(self, **kwargs):
        self.bot.failures.hit("user_create")
        thread = Thread(
            self.bot, self.bot.next_id(), self, kwargs["name"], private=True
        )
        self.all_threads.append(thread)
        self.bot.channels[thread.id] = thread
        self.bot.failures.hit("user_create", after=True)
        return thread


class Forum(TextChannel):
    type = discord.ChannelType.forum

    def __init__(self, bot, id, guild, *, senior=False):
        super().__init__(bot, id, guild)
        self.available_tags = []
        self.senior = senior

    def permissions_for(self, who):
        if who.id in self.role_permissions:
            return self.role_permissions[who.id]
        if who.id == 999:
            return discord.Permissions.all()
        return discord.Permissions(
            view_channel=who.id == 20 if self.senior else who.id in {10, 20}
        )

    async def edit(self, **kwargs):
        self.available_tags = kwargs.get("available_tags", self.available_tags)

    async def create_thread(self, **kwargs):
        self.bot.failures.hit("forum_create")
        thread = Thread(self.bot, self.bot.next_id(), self, kwargs["name"])
        thread.applied_tags = kwargs.get("applied_tags", [])
        message = Message(
            self.bot,
            thread,
            thread.id,
            embed=kwargs["embed"],
            view=kwargs.get("view"),
        )
        thread.messages.append(message)
        self.all_threads.append(thread)
        self.bot.channels[thread.id] = thread
        self.bot.failures.hit("forum_create", after=True)
        return SimpleNamespace(thread=thread, message=message)


class Thread(TextChannel):
    type = discord.ChannelType.public_thread

    def __init__(self, bot, id, parent, name, private=False):
        super().__init__(bot, id, parent.guild)
        self.parent, self.name = parent, name
        self.parent_id = parent.id
        self.owner_id = 999
        self.archived = self.locked = False
        self.private = private
        self.members, self.applied_tags, self.edits = set(), [], []

    async def edit(self, **changes):
        self.bot.failures.hit("thread_edit")
        self.bot.calls.append(("thread_edit", self.id))
        for key, value in changes.items():
            setattr(self, key, value)
        self.edits.append(changes)
        self.bot.failures.hit("thread_edit", after=True)

    async def add_user(self, user):
        self.bot.failures.hit("user_add")
        self.members.add(user.id)
        self.bot.failures.hit("user_add", after=True)

    async def remove_user(self, user):
        self.bot.failures.hit("user_remove")
        self.members.discard(user.id)
        self.bot.failures.hit("user_remove", after=True)

    async def send(self, content="", **kwargs):
        label = "user_send" if self.private else "post_send"
        self.bot.failures.hit(label)
        message = Message(
            self.bot,
            self,
            self.bot.next_id(),
            content,
            kwargs.get("embed"),
            kwargs.get("view"),
        )
        message.file = kwargs.get("file")
        self.messages.append(message)
        self.bot.failures.hit(label, after=True)
        return message


class CursorObjects:
    def __init__(self, items):
        self.items = items

    def __aiter__(self):
        async def generate():
            for item in self.items:
                yield item

        return generate()


class User:
    def __init__(self, bot, id):
        self.bot, self.id = bot, id
        self.messages = []

    async def send(self, text, **kwargs):
        self.bot.failures.hit("dm")
        self.messages.append(text)
        self.bot.failures.hit("dm", after=True)


class Bot:
    def __init__(self):
        self.user = SimpleNamespace(id=999, bot=True)
        self.failures = Failures()
        # Discord calls that cost a request, for tests about what a healthy case costs.
        self.calls = []
        self._seq = 1000
        guild = Guild()
        self.channels = {
            100: TextChannel(self, 100, guild),
            200: Forum(self, 200, guild),
            300: Forum(self, 300, guild, senior=True),
            400: Forum(self, 400, guild),
            500: TextChannel(self, 500, guild),
        }
        self.users = {}

    def next_id(self):
        self._seq += 1
        return self._seq

    def get_channel(self, id):
        return self.channels.get(id)

    def get_guild(self, id):
        return self.channels[100].guild if id == 123 else None

    async def fetch_channel(self, id):
        if id not in self.channels:
            raise discord.NotFound(
                SimpleNamespace(status=404, reason="Not Found"), "Unknown Channel"
            )
        return self.channels[id]

    def get_user(self, id):
        return self.users.get(id)

    async def fetch_user(self, id):
        return self.users.setdefault(id, User(self, id))


class Claims:
    def __init__(self):
        self.keys = set()

    async def claim_idempotency(self, key, ttl_seconds=600):
        if key in self.keys:
            return False
        self.keys.add(key)
        return True


class Players:
    """The slice of ``MongoStore`` the cases core reads players through."""

    def __init__(self):
        self.by_uuid, self.by_discord, self.by_pid = {}, {}, {}

    async def find_player_by_uuid(self, uuid):
        return self.by_uuid.get(uuid)

    async def find_player_by_pid(self, pid):
        return self.by_pid.get(pid)

    async def find_players_by_discord_id(self, discord_id):
        return self.by_discord.get(str(discord_id), [])


class Response:
    def __init__(self, events):
        self.events = events

    async def defer(self, **kwargs):
        self.events.append(("defer", kwargs))

    async def send_message(self, content, **kwargs):
        self.events.append(("message", content, kwargs))

    async def send_modal(self, modal):
        self.events.append(("modal", modal))

    async def edit_message(self, **changes):
        self.events.append(("edit_message", changes))


class Followup:
    def __init__(self, events):
        self.events = events

    async def send(self, content, **kwargs):
        self.events.append(("followup", content, kwargs))

    def texts(self):
        return [event[1] for event in self.events if event[0] == "followup"]


class Interaction:
    def __init__(
        self, bot, *, user=None, locale=discord.Locale.american_english, id=555
    ):
        self.client, self.user, self.locale, self.id = bot, user or member(), locale, id
        self.events = []
        self.response, self.followup = Response(self.events), Followup(self.events)
        self.guild_id = 123
        self.type = discord.InteractionType.component

    async def edit_original_response(self, **changes):
        self.events.append(("edit_original", changes))


def settings(**kwargs):
    defaults = {
        "discord_bot_token": "fake-token",
        "discord_admin_role_id": 10,
        "discord_general_admin_role_id": 20,
        "discord_map_reviewer_role_id": 30,
        "discord_private_channel_id": 50,
        "discord_guild_id": 123,
        "cases_panel_channel_id": 100,
        "cases_staff_forum_id": 200,
        "cases_senior_forum_id": 300,
        "discord_maps_forum_id": 400,
        "cases_complaint_thread_channel_id": 500,
    }
    return Settings(**{**defaults, **kwargs})


async def core(*, flow=None, tags=True, **kwargs):
    db, bot, registry = Database(), Bot(), FlowRegistry()
    cfg = settings(**kwargs)
    if tags:
        # The two staff forums as the start-up checks leave them; the maps forum is the
        # owner's and starts without the bot's tags.
        for forum_id in (200, 300):
            bot.channels[forum_id].available_tags = [
                discord.ForumTag(name=name) for name in FORUM_TAGS
            ]
    store = CaseStore(SimpleNamespace(database=db))
    await store.ensure_indexes()
    if flow:
        registry.register(flow)
        registry.enable(flow.kind)
    players = Players()
    service = CaseService(
        store=store,
        settings=cfg,
        registry=registry,
        threads=CaseThreads(bot, cfg),
        notifier=CaseNotifier(bot, cfg, find_player=players.find_player_by_uuid),
        claims=Claims(),
        player_store=players,
        now=lambda: NOW,
    )
    bot.settings = cfg
    bot.container = SimpleNamespace(cases=service)
    return service, db, bot
