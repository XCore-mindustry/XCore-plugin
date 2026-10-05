from __future__ import annotations

from datetime import UTC, datetime, timedelta

from xcore_discord_bot.dto import BanRecord, MuteRecord, PlayerRecord
from xcore_discord_bot.game_stats import GameStats, ModeStats
from xcore_discord_bot.leagues import LEAGUES, league_for, next_league
from xcore_discord_bot.rating_store import Placing, Season
from xcore_discord_bot.season_embeds import build_ratings_field, league_progress
from xcore_discord_bot.stats_embed import StaffNotes, StatsView, build_stats_embed

SOON = datetime.now(UTC) + timedelta(days=30)


def _placing(ladder: str, rating: int, **extra: int) -> Placing:
    season = Season(
        ladder=ladder,
        number=2,
        name="Autumn",
        status="ACTIVE",
        starts_at=datetime(2026, 9, 1, tzinfo=UTC),
        ends_at=SOON,
        matches=400,
        participants=None,
        podium=(),
    )
    values = {"matches": 35, "wins": 20, "rank": 4, "participants": 120} | extra
    return Placing(season=season, rating=rating, **values)


def _fields(embed) -> dict[str, str]:
    return {field.name: field.value for field in embed.fields}


def test_leagues_follow_the_thresholds_of_the_game() -> None:
    assert league_for(0).name == "Scrap"
    assert league_for(799).name == "Scrap"
    assert league_for(800).name == "Copper"
    assert league_for(1650).name == "Titanium"
    assert league_for(9999).name == "Surge Alloy"
    assert next_league(league_for(1650)).name == "Thorium"
    assert next_league(LEAGUES[-1]) is None


def test_league_progress_shows_the_way_to_the_next_league() -> None:
    assert league_progress(1650) == "▰▰▱▱▱▱▱▱ `150` to Thorium"
    assert league_progress(1600) == "▱▱▱▱▱▱▱▱ `200` to Thorium"
    assert league_progress(2600) == "top league"


def test_ratings_field_names_the_league_place_and_peak() -> None:
    text = build_ratings_field([_placing("minipvp", 1650, peak_rating=1702)])

    head, standing, progress = text.split("\n")
    assert head.startswith("**Mini-PvP** · Autumn · ends <t:")
    assert standing == "Titanium `1650` · #4 of 120 · 20/35 wins (57%)"
    assert progress == "▰▰▱▱▱▱▱▱ `150` to Thorium · peak `1702`"
    assert build_ratings_field([]) == "No rated matches this season"


def test_stats_embed_of_an_active_player() -> None:
    player = PlayerRecord(
        pid=-12,
        nickname="[scarlet]Vor*tex[]",
        username="vortex",
        description="[accent]Hello[]\n_there_",
        total_play_time=1501,
        hexed_rank=3,
        hexed_points=22,
        unlocked_badges=("developer", "mystery"),
        active_badge="developer",
        is_admin=True,
        discord_id="4242",
        discord_linked_at=1_700_000_000_000,
        online=True,
        online_since=1_750_000_000_000,
        online_server="mini-pvp",
        created_at=1_600_000_000_000,
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
        hexed=ModeStats(games=30, wins=5, best_placement=1, top3=9),
    )

    embed = build_stats_embed(
        StatsView(
            player=player,
            placings=[_placing("minipvp", 1650), _placing("hexed", 1210)],
            games=games,
            show_discord=True,
            avatar_url="https://cdn.example/avatar.png",
        )
    )

    # colour tags are gone and the markdown of a name does not format the embed
    assert embed.title == "Vor\\*tex"
    assert embed.description.split("\n") == [
        "`#-12` · `@vortex` · 🎖️ Developer · 🛡️ Admin",
        "> Hello \\_there\\_",
        "🟢 **Online** on `mini-pvp` · joined <t:1750000000:R>",
    ]
    # the best league of the season colours the embed
    assert embed.color.value == league_for(1650).color
    assert embed.thumbnail.url == "https://cdn.example/avatar.png"

    fields = _fields(embed)
    assert fields["⏱️ Playtime"] == "`1d 1h 1m`"
    assert fields["📅 First seen"] == "<t:1600000000:D>"
    assert fields["🔗 Discord"] == "<@4242>\nlinked <t:1700000000:R>"
    assert fields["🎮 Games"].split("\n") == [
        "`1,234` played · `567` won · `46%` win rate",
        "⚔️ **PvP** · 1 game · 1 win (100%)",
        "🛡️ **Survival** · 40 games · best wave 120 · average 45",
        "⬡ **Hexed** · 30 games · 5 wins · best `#1` · top 3 ×9",
    ]
    assert fields["🧱 Building"].split("\n") == [
        "Blocks: `12,345` built · `1,200` taken apart · `300` lost",
        "Units: `40` produced · `25` lost",
    ]
    assert fields["🏆 Season ratings"].count("\n\n") == 1
    assert fields["⬡ Hexed rank"] == "**Veteran**\n22/25 wins to Devastator"
    assert fields["🎖️ Badges"] == "**Developer** (shown) · mystery"
    assert "🔒 Staff notes" not in fields


def test_stats_embed_of_a_new_player_leaves_the_empty_sections_out() -> None:
    embed = build_stats_embed(
        StatsView(
            player=PlayerRecord(pid=0, nickname="", updated_at=1_750_000_000_000),
            placings=[],
            games=GameStats(),
        )
    )

    assert embed.title == "Unknown"
    assert embed.description.split("\n") == [
        "`#0`",
        "⚫ Offline · profile updated <t:1750000000:R>",
    ]
    fields = _fields(embed)
    # the Discord link is not for everyone to see
    assert list(fields) == [
        "⏱️ Playtime",
        "📅 First seen",
        "🏆 Season ratings",
        "🎮 Games",
    ]
    assert fields["📅 First seen"] == "Unknown"
    assert fields["🏆 Season ratings"] == "No rated matches this season"
    assert fields["🎮 Games"] == "No recorded games yet"


def test_discord_link_is_shown_only_when_asked_for() -> None:
    player = PlayerRecord(pid=5, nickname="Ann", discord_id="4242")
    other = PlayerRecord(pid=6, nickname="Alt")
    hidden = build_stats_embed(
        StatsView(player=player, avatar_url="https://cdn.example/a.png", other_accounts=[other])
    )
    shown = build_stats_embed(
        StatsView(
            player=PlayerRecord(pid=5, nickname="Ann"), show_discord=True, other_accounts=[other]
        )
    )

    assert "🔗 Discord" not in _fields(hidden)
    assert hidden.thumbnail.url is None
    assert hidden.footer.text is None
    assert _fields(shown)["🔗 Discord"] == "Not linked"
    assert shown.footer.text == "Also linked: #6 Alt"


def test_staff_notes_show_only_the_punishments_still_running() -> None:
    player = PlayerRecord(pid=5, nickname="Ann", admin_source="DISCORD_ROLE")
    ban = BanRecord(
        name="Ann",
        admin_name="Mod_1",
        reason="grief `core`",
        expire_date=datetime.now(UTC) + timedelta(days=2),
    )
    # stored without a timezone, as Mongo hands dates back
    old_mute = MuteRecord(
        name="Ann",
        admin_name="Mod",
        reason="spam",
        expire_date=datetime.now(UTC).replace(tzinfo=None) - timedelta(hours=1),
    )

    notes = _fields(
        build_stats_embed(
            StatsView(player=player, staff=StaffNotes(ban=ban, mute=old_mute))
        )
    )["🔒 Staff notes"]

    assert "🔨 Ban: until <t:" in notes
    assert "└ `grief 'core'` by Mod\\_1" in notes
    assert "🔇 Mute: none" in notes
    # the source says nothing about a player who is not an admin
    assert "Admin source" not in notes

    failed = _fields(
        build_stats_embed(StatsView(player=player, staff=StaffNotes(loaded=False)))
    )["🔒 Staff notes"]
    assert "🔨 Ban and mute: Unavailable right now" in failed


def test_stats_embed_keeps_a_long_name_and_bio_inside_the_limits() -> None:
    embed = build_stats_embed(
        StatsView(
            player=PlayerRecord(
                pid=1,
                nickname="n" * 240,
                custom_nickname="c" * 240,
                description="b" * 900,
            )
        )
    )

    assert len(embed.title) == 256
    assert embed.title.endswith("…")
    assert len(embed.description) < 400
