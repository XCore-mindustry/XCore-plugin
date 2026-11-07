"""The replies players read outside cases: /stats, /season, /servers in their language."""

from __future__ import annotations

from dataclasses import replace
from datetime import UTC, datetime, timedelta
from types import SimpleNamespace
from typing import get_args

import pytest

from xcore_discord_bot.badges import BADGES
from xcore_discord_bot.dto import PlayerRecord
from xcore_discord_bot.game_stats import GameStats, ModeStats
from xcore_discord_bot.i18n import SUPPORTED
from xcore_discord_bot.i18n.loader import get_bundle
from xcore_discord_bot.leagues import LEAGUES
from xcore_discord_bot.presentation import (
    HEXED_RANKS,
    build_servers_embed,
    format_minutes,
)
from xcore_discord_bot.rating_store import Placing, RankedStanding, Season
from xcore_discord_bot.registry import ServerInfo
from xcore_discord_bot.season_embeds import (
    build_ratings_field,
    build_season_info_embed,
    build_season_top_embed,
    league_progress,
)
from xcore_discord_bot.server_views import MapSortMode, ServersView
from xcore_discord_bot.stats_embed import StaffNotes, StatsView, build_stats_embed

NBSP = "\xa0"


def _season(status: str = "ACTIVE") -> Season:
    return Season(
        ladder="minipvp",
        number=2,
        name=None,
        status=status,
        starts_at=datetime(2026, 9, 1, tzinfo=UTC),
        ends_at=datetime.now(UTC) + timedelta(days=30),
        matches=400,
        participants=None,
        podium=(),
    )


def _fields(embed) -> dict[str, str]:
    return {field.name: field.value for field in embed.fields}


@pytest.mark.parametrize("locale", SUPPORTED)
def test_keys_built_from_ids_exist_in_every_locale(locale: str) -> None:
    """The catalog test only sees literal keys; these are put together from ids."""
    keys = (
        [f"league-{league.id}" for league in LEAGUES]
        + [f"stats-hexed-rank-{rank_id}" for rank_id, _ in HEXED_RANKS]
        + [f"badges-name-{badge.id}" for badge in BADGES]
        + [f"servers-sort-{mode}" for mode in ("players", "name")]
        + [f"maps-list-sort-{mode}" for mode in get_args(MapSortMode)]
    )
    bundle = get_bundle(locale)

    assert [key for key in keys if not bundle.has_message(key)] == []


def test_playtime_reads_in_the_language() -> None:
    assert format_minutes(1501) == "1d 1h 1m"
    assert format_minutes(1501, "ru") == "1 д 1 ч 1 мин"
    assert format_minutes(61, "uk") == "1 год 1 хв"


def test_stats_embed_in_russian() -> None:
    player = PlayerRecord(
        pid=-12,
        nickname="Vortex",
        total_play_time=1501,
        hexed_rank=3,
        hexed_points=22,
        unlocked_badges=("developer", "mystery"),
        active_badge="developer",
        discord_id="4242",
        discord_linked_at=1_700_000_000_000,
        online=True,
        online_since=1_750_000_000_000,
        online_server="mini-pvp",
        created_at=1_600_000_000_000,
        leaderboard=False,
    )
    games = GameStats(
        games=1234,
        wins=567,
        blocks_built=12345,
        blocks_deconstructed=1200,
        blocks_destroyed=300,
        units_produced=40,
        units_destroyed=25,
        pvp=ModeStats(games=1, wins=1),
        survival=ModeStats(games=40, best_wave=120, average_wave=45),
        hexed=ModeStats(games=22, wins=5, best_placement=1, top3=9),
    )

    embed = build_stats_embed(
        StatsView(
            player=player,
            placings=[],
            games=games,
            staff=StaffNotes(),
            show_discord=True,
            other_accounts=[PlayerRecord(pid=9, nickname="")],
            locale="ru",
        )
    )

    assert embed.description.split("\n") == [
        "`#-12` · 🎖️ Разработчик",
        "🟢 **В сети** на `mini-pvp` · вход <t:1750000000:R>",
    ]
    fields = _fields(embed)
    assert fields["⏱️ Время в игре"] == "`1 д 1 ч 1 мин`"
    assert fields["🔗 Discord"] == "<@4242>\nпривязан <t:1700000000:R>"
    assert fields["🏆 Рейтинг сезона"] == "В этом сезоне нет рейтинговых матчей"
    # the grouping and the plural forms are the language's own
    assert fields["🎮 Игры"].split("\n") == [
        f"Сыграно `1{NBSP}234` · выиграно `567` · `46%` побед",
        "⚔️ **PvP** · 1 игра · 1 победа (100%)",
        "🛡️ **Выживание** · 40 игр · лучшая волна 120 · в среднем 45",
        "⬡ **Hexed** · 22 игры · 5 побед · лучшее место `#1` · топ-3 ×9",
    ]
    assert fields["🧱 Строительство"].split("\n") == [
        f"Блоки: построено `12{NBSP}345` · разобрано `1{NBSP}200` · потеряно `300`",
        "Юниты: произведено `40` · потеряно `25`",
    ]
    assert fields["⬡ Ранг Hexed"] == "**Ветеран**\n22/25 побед до ранга «Опустошитель»"
    assert fields["🎖️ Значки"] == "**Разработчик** (показан) · mystery"
    assert fields["🔒 Заметки для персонала"].split("\n") == [
        "🔨 Бан: нет",
        "🔇 Мут: нет",
        "🌐 Язык: `auto` · переводчик: `off`",
        "🙈 Скрыт из таблиц лидеров",
    ]
    assert embed.footer.text == "Также привязаны: #9 Неизвестно"


def test_staff_notes_name_a_punishment_in_ukrainian() -> None:
    ban = SimpleNamespace(
        expire_date=datetime.now(UTC) + timedelta(days=1),
        reason="Rule 1",
        admin_name="Admin",
    )
    embed = build_stats_embed(
        StatsView(
            player=PlayerRecord(pid=5, nickname="Vortex"),
            staff=StaffNotes(ban=ban),  # type: ignore[arg-type]
            locale="uk",
        )
    )

    notes = _fields(embed)["🔒 Нотатки для персоналу"].split("\n")
    assert notes[0].startswith("🔨 Бан: до ")
    assert notes[1] == "└ `Rule 1` — Admin"
    assert notes[2] == "🔇 Мут: немає"
    # sections that could not be loaded say so in the language too
    assert _fields(embed)["🎮 Ігри"] == "Зараз недоступно"


def test_season_ratings_name_the_leagues_of_the_game() -> None:
    placing = Placing(
        season=_season(),
        rating=1650,
        matches=35,
        wins=20,
        rank=4,
        participants=120,
        peak_rating=1702,
    )

    head, standing, progress = build_ratings_field([placing], "ru").split("\n")

    assert head.startswith("**Mini-PvP** · Сезон 2 · закончится <t:")
    assert standing == "Титан `1650` · #4 из 120 · 20/35 побед (57%)"
    assert progress == "▰▰▱▱▱▱▱▱ `150` до лиги «Торий» · пик `1702`"
    assert league_progress(9999, locale="uk") == "найвища ліга"
    assert league_progress(100, locale="uk").endswith("`700` до ліги «Мідь»")


@pytest.mark.parametrize(
    ("players", "footer"),
    [
        (1, "1 гравець у цьому сезоні"),
        (3, "3 гравці у цьому сезоні"),
        (11, "11 гравців у цьому сезоні"),
        (21, "21 гравець у цьому сезоні"),
        (1001, f"1{NBSP}001 гравець у цьому сезоні"),
    ],
)
def test_season_top_counts_players_in_ukrainian(players: int, footer: str) -> None:
    standing = RankedStanding(1, "uuid", "Ace", 7, 1800, 10, 8)

    embed = build_season_top_embed(
        _season(), [standing], participants=players, locale="uk"
    )

    assert embed.title == "🏅 Mini-PvP · Сезон 2 · Топ"
    assert embed.description == "🥇 **Ace** (#7) · `1800` · 8/10 перемог"
    assert embed.footer.text == footer


def test_season_info_in_russian() -> None:
    season = replace(_season(), matches=12345)
    embed = build_season_info_embed(
        season, participants=1234, now=datetime.now(UTC), locale="ru"
    )

    assert embed.title == "📊 Mini-PvP · Сезон 2"
    fields = _fields(embed)
    assert list(fields) == ["Начало", "Конец", "Игроки", "Матчи"]
    assert fields["Игроки"] == f"`1{NBSP}234`"
    assert fields["Матчи"] == f"`12{NBSP}345`"
    empty = build_season_top_embed(_season(), [], participants=0, locale="ru")
    assert empty.description == "В этом сезоне ещё никто не играл."


async def test_servers_list_in_russian() -> None:
    server = ServerInfo("alpha", 123, 3, 30, "155.4", "play.example.com", 6567, 0.0)
    registry = SimpleNamespace(get_all_servers=lambda: [server])

    view = ServersView(registry, sort_mode="players", locale="ru")  # type: ignore[arg-type]
    embed = view.build_embed()

    assert embed.title == "Серверы онлайн"
    assert "🔌 Адрес: `play.example.com:6567`" in embed.fields[0].value
    assert embed.footer.text == (
        "Сортировка: по игрокам • Серверов: 1 • Игроков онлайн: 3"
    )
    assert [item.label for item in view.children] == [
        "Обновить",
        "Сортировка: по игрокам",
    ]
    assert build_servers_embed([], sort_mode="name", locale="uk").description == (
        "Зараз немає підключених серверів."
    )
