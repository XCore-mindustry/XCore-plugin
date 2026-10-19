import type { Dictionary } from "./ru";

const en: Dictionary = {
  meta: {
    title: "XCore — a network of Mindustry servers",
    description:
      "Nine modes, one profile: Mini-PvP, HexedCore, Red vs Blue and more. Seasonal rating, badges, cross-server chat with translation, and open source.",
  },
  skip: "Skip to content",
  langLabel: "Language",

  pass: {
    tagline: "A network of Mindustry servers. One profile for every mode.",
    example: "Sample profile",
    exampleNote: "The player is made up, the fields are real: this is what /stats shows.",
    rating: "Season rating",
    league: "League",
    nextLeague: (points: number, league: string) => `${points} to ${league}`,
    ladderNote: "Mini-PvP and HexedCore rate separately. The season runs to January 1, 2027; the podium earns the Season Champion badge.",
    playtime: "Playtime",
    hours: (h: number) => `${h} h`,
    matches: "Rated matches",
    badges: "Badges",
    modes: "Modes",
    modesNote: "marked as you open them",
  },

  route: {
    title: "How to join",
    steps: ["Play", "Join Game", "Community Servers"],
    target: "XCore",
    copy: "Copy “XCore” for the search",
    copied: "Copied. Paste it into Search above the server list.",
    failed: "Couldn't copy. Type XCore into the search yourself.",
    note: "No IP needed: the XCore group is in the game's public server list.",
    discord: "Discord",
  },

  dock: {
    label: "Sections",
    modes: "Modes",
    network: "Network",
    fair: "Fair play",
    dev: "Developers",
    pass: "Profile",
  },

  leagues: {
    scrap: "Scrap",
    copper: "Copper",
    lead: "Lead",
    graphite: "Graphite",
    silicon: "Silicon",
    titanium: "Titanium",
    thorium: "Thorium",
    plastanium: "Plastanium",
    "phase-fabric": "Phase Fabric",
    "surge-alloy": "Surge Alloy",
  },
  badges: {
    developer: "Developer",
    translator: "Translator",
    "map-maker": "Map Maker",
    contributor: "Contributor",
    "bug-finder": "Bug Finder",
    "event-winner": "Event Winner",
    veteran: "Veteran",
    "season-champion": "Season Champion",
  },
  locked: "locked",

  modes: {
    title: "Modes",
    lede: "Each mode plays by its own rules. Your profile, badges and chat carry across.",
    count: "modes, one profile",
    commands: "Commands",
    items: {
      pvp: {
        name: "Mini-PvP",
        line: "Team against team",
        body: "Teams build bases on small maps and go at each other until one is left. Every match moves your rating, and the best of each season win prizes.",
        facts: [
          ["Rating", "ELO, in seasons"],
          ["Season top 10", "10+ rated matches"],
        ],
        commands: [
          ["/matches", "your rated matches"],
          ["/top", "the season table"],
          ["/season", "time left and prizes"],
        ],
      },
      surv: {
        name: "Mini-Surv",
        line: "Survival against waves",
        body: "Classic survival with the whole server: one base, one core and waves of enemies. Tired of waiting for a wave? Vote to call it early.",
        facts: [
          ["Maps", "some made by players"],
          ["Waves", "early by vote"],
        ],
        commands: [
          ["/vnw", "vote for the next wave"],
          ["/rtv", "vote to change the map"],
        ],
      },
      attack: {
        name: "Mini-Attack",
        line: "Storm the enemy base",
        body: "An AI-run enemy base is already on the map. Get mining going, build an army and take down every enemy core.",
        facts: [
          ["Goal", "every enemy core"],
          ["Enemy", "AI"],
        ],
        commands: [
          ["/maps", "the server's maps"],
          ["/rtv", "vote to change the map"],
        ],
      },
      event: {
        name: "Event",
        line: "The events server",
        body: "Network events run here, each with its own maps and rules. Between events the server may be off; announcements go out on Discord.",
        facts: [
          ["Open", "during events"],
          ["Winners get", "the Event Winner badge"],
        ],
        commands: [["/events", "every event on the servers"]],
      },
      hexed: {
        name: "HexedCore",
        line: "Hexes, free for all",
        body: "The map is split into hexes with a core in the middle of each. Destroy a neutral or enemy core and the hex is yours; you can only build where the nearest core is yours. Take every hex or be the last one standing to win. If time runs out, whoever holds the most hexes wins.",
        facts: [
          ["Rating", "ELO by placement"],
          ["Leagues", "Scrap to Surge Alloy"],
          ["Flying", "spiders and ships"],
          ["Turret damage", "to blocks reduced"],
        ],
        commands: [
          ["/hexed", "mode menu and guide"],
          ["/status", "round status"],
          ["/leagues", "leagues"],
          ["/surrender", "give up"],
        ],
      },
      rvb: {
        name: "Red vs Blue",
        line: "Survivors against the virus",
        body: "Blue are the last survivors on Serpulo: they buy units at the lab, pick a class and build stations. Whoever dies joins red, the infected. Bosses rise from the red side.",
        facts: [
          ["Blue", "survive and build"],
          ["Red", "the infected and bosses"],
        ],
        commands: [
          ["/c", "pick a class"],
          ["/s", "build stations"],
          ["/b", "pick a block"],
        ],
      },
      siege: {
        name: "Siege",
        line: "Attack against defence",
        body: "Green storms, blue holds the base. Attackers must destroy the core before the timer runs out; defenders must last until it does.",
        facts: [
          ["Team change", "once per game"],
          ["Foreshadows", "at most 4 on the server"],
        ],
        commands: [["/changeteam", "switch team"]],
      },
      asthosus: {
        name: "Asthosus",
        line: "A mod with a new planet",
        body: "A server running the Asthosus mod, which adds a new planet to the game. Modded servers aren't allowed on the public list, so you join by IP with the mod installed.",
        facts: [
          ["Address", "159.195.205.212:7008"],
          ["Needs", "the Asthosus mod"],
        ],
        commands: [],
      },
      lab: {
        name: "Quadset",
        line: "Experimental mode",
        body: "Free for all, with new content you won't find in the regular game. This is the network's experimental slot: the mode on it may change in about a month. Players suggest what goes into Quadset on the #quadset-content forum on Discord.",
        facts: [
          ["Slot", "experimental, the mode changes"],
          ["Content", "suggested by players"],
        ],
        commands: [],
      },
    },
    rvbDiscord: "Red vs Blue has its own Discord",
    asthosusMod: "The Asthosus mod on GitHub",
    labSuggest: "Suggest content on Discord",
  },

  network: {
    title: "One network",
    lede: "Your profile, chat and Discord link are shared by every XCore server.",
    languages: "menu languages",
    languagesNote: "Translations happen on Weblate, and anyone can help.",
    weblate: "Open Weblate",
    rows: [
      ["/g", "Global chat", "Talk to players on other servers of the network. /t for your team only."],
      ["/tr en", "Chat translation", "Other players' messages arrive in your language; auto detects it for you."],
      ["/discord link", "Discord", "Link your game account with a one-time code."],
      ["/msg", "Private messages", "Inbox, replies and a block list: /inbox, /reply."],
      ["/rtv", "Map votes", "Like or dislike maps, and vote to change the map."],
    ],
  },

  fair: {
    title: "Fair play",
    lede: "Every block remembers who placed it and who broke it.",
    body: "TileLogger records every change on the map. A moderator opens the history of any tile and rolls a grief back in one go.",
    logTitle: "Tile history",
    example: "sample",
    count: "blocks in one rollback",
    log: [
      ["21:04:12", "Ferrum", "placed", "Conveyor"],
      ["21:05:40", "griefer_17", "broke", "Conveyor"],
      ["21:06:02", "moderator", "rolled back", "34 blocks"],
    ],
    votekick: "Players can also vote a rule-breaker out. The vote belongs to those with enough playtime.",
  },

  dev: {
    title: "For developers",
    lede: "Everything XCore runs on is open source. Java 25, Mindustry v160.",
    count: "open repositories",
    libs: {
      "xcore-ui": "Reactive server-driven menus: state in records, partial updates without flicker.",
      "xcore-plugin-template": "A Java 25 plugin template in the XCore style.",
      "mindustry-testkit": "Test plugins without a client: a headless world and a player simulator.",
      "cloud-mindustry": "Incendo Cloud v2 for Mindustry commands.",
      FluBundle: "Project Fluent localization, plus content names for a server.",
      fluent: "Project Fluent for Java.",
      "xcore-protocol": "Shared message schemas between XCore services.",
      TileLogger: "Block history and rollback, with a C++20 core.",
    },
    snippet: "Add xcore-ui",
    maven: "Maven repository",
    github: "All repositories",
  },

  close: {
    title: "See you on the server",
    discord: "Join the Discord",
  },

  footer: {
    translate: "Translations",
    donate: "Support us",
    rvb: "Red vs Blue Discord",
    legal:
      "Mindustry is a game by Anuken. Sprites and icons come from the game (GPL-3.0). XCore is a player community, not affiliated with the game's developer.",
  },
};

export default en;
