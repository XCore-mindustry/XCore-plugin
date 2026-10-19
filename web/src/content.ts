// Facts shared by every language: links, colours and sprites, mirrored from the plugins.

import {formatErrors} from "next/dist/server/mcp/tools/utils/format-errors";

export const links = {
  discord: "https://discord.gg/RUMCCa9QAC",
  rvbDiscord: "https://discord.gg/UdnuFetcNt",
  asthosus: "https://github.com/Catana791/Asthosus",
  github: "https://github.com/XCore-mindustry",
  donate: "https://donatello.to/xcore",
  weblate: "https://weblate.x-core.org/",
  maven: "https://maven.x-core.org",
};

/** The kit's topic colours (XCore-plugin ui/kit/Accent.java): text colour and its dark band. */
export const accents = {
  gold: ["#ffd37f", "#4d4026"],
  blue: ["#7fc8ff", "#243c4f"],
  green: ["#98d982", "#2c4227"],
  purple: ["#d79bff", "#402c4f"],
  red: ["#ff8f8f", "#4f2a2a"],
  orange: ["#ffb074", "#4f3524"],
  teal: ["#7fe0d0", "#24443f"],
  gray: ["#c9ced9", "#353a48"],
} as const;
export type Accent = keyof typeof accents;

/** Glyphs of Mindustry's icon font (core/assets-raw/fontgen/config.json). */
export const glyph = {
  wrench: "",
  bookOpen: "",
  map: "",
  star: "",
  warning: "⚠",
  effect: "",
  play: "",
  players: "",
  chat: "",
  discord: "",
  github: "",
  link: "",
  right: "",
  down: "",
  copy: "",
  ok: "",
  lock: "",
  hammer: "",
  terminal: "",
  host: "",
  inbox: "",
  list: "",
  eye: "",
  undo: "",
  modePvp: "",
  modeAttack: "",
  modeSurvival: "",
  admin: "",
  planeOutline: "",
  planet: "",
  defense: "",
  crafting: "",
  download: "",
} as const;

export type BadgeId =
  | "developer"
  | "translator"
  | "map-maker"
  | "contributor"
  | "bug-finder"
  | "event-winner"
  | "veteran"
  | "season-champion";

/** Selectable badges in XCore-plugin player/Badge.java, with their tag colours. */
export const badges: { id: BadgeId; color: string; glyph?: string; sprite?: string; mask?: string }[] = [
  { id: "developer", color: "#86dca2", glyph: glyph.wrench },
  { id: "translator", color: "#79d7ff", glyph: glyph.bookOpen },
  { id: "map-maker", color: "#caa56b", glyph: glyph.map },
  { id: "contributor", color: "#ffd700", glyph: glyph.star },
  { id: "bug-finder", color: "#c8ff8c", glyph: glyph.warning },
  { id: "event-winner", color: "#ffef88", sprite: "/sprites/items/surge-alloy.png" },
  { id: "veteran", color: "#00ffff", mask: "/sprites/status-overdrive.png" },
  { id: "season-champion", color: "#ffd37f", glyph: glyph.effect },
];

/** Rating leagues in XCore-plugin rating/RatingLeague.java: item sprite and the rating that opens it. */
export const leagues = [
  { id: "scrap", from: 100 },
  { id: "copper", from: 800 },
  { id: "lead", from: 1000 },
  { id: "graphite", from: 1200 },
  { id: "silicon", from: 1400 },
  { id: "titanium", from: 1600 },
  { id: "thorium", from: 1800 },
  { id: "plastanium", from: 2000 },
  { id: "phase-fabric", from: 2200 },
  { id: "surge-alloy", from: 2500 },
] as const;

export type ModeId = "pvp" | "surv" | "attack" | "event" | "hexed" | "rvb" | "siege" | "asthosus" | "lab";

/** The network's servers in port order, as #information lists them; glyphs as in ServerRegistryService. */
export const modes: { id: ModeId; accent: Accent; glyph: string; sprites: { src: string; size: number; scale: number }[] }[] = [
  {
    id: "pvp",
    accent: "red",
    glyph: glyph.modePvp,
    sprites: [
      { src: "/sprites/dagger.png", size: 48, scale: 3 },
      { src: "/sprites/dagger-crux.png", size: 48, scale: 3 },
    ],
  },
  { id: "surv", accent: "green", glyph: glyph.modeSurvival, sprites: [{ src: "/sprites/ripple.png", size: 96, scale: 2 }] },
  { id: "attack", accent: "orange", glyph: glyph.modeAttack, sprites: [{ src: "/sprites/mace.png", size: 64, scale: 3 }] },
  { id: "event", accent: "gray", glyph: glyph.star, sprites: [{ src: "/sprites/mega.png", size: 100, scale: 2 }] },
  { id: "hexed", accent: "purple", glyph: glyph.admin, sprites: [{ src: "/sprites/core-shard.png", size: 96, scale: 2 }] },
  {
    id: "rvb",
    accent: "blue",
    glyph: glyph.planeOutline,
    sprites: [
      { src: "/sprites/nova-blue.png", size: 56, scale: 3 },
      { src: "/sprites/crawler-crux.png", size: 48, scale: 3 },
    ],
  },
  {
    id: "siege",
    accent: "gold",
    glyph: glyph.defense,
    sprites: [
      { src: "/sprites/zenith-green.png", size: 112, scale: 1 },
      { src: "/sprites/foreshadow.png", size: 128, scale: 2 },
    ],
  },
  { id: "asthosus", accent: "teal", glyph: glyph.planet, sprites: [{ src: "/sprites/accelerator.png", size: 224, scale: 1 }] },
  // The experimental slot (port 7009): its mode changes every month or so, the id stays.
  { id: "lab", accent: "green", glyph: glyph.crafting, sprites: [{ src: "/sprites/quad.png", size: 220, scale: 1 }] },
];

/** The synthetic player on the pass. Every field exists in the real profile; the values are made up. */
export const sample = {
  name: "Ferrum",
  id: 4821,
  username: "ferrum",
  rating: 1487,
  hours: 212,
  matches: 46,
  activeBadge: "map-maker" as BadgeId,
  unlocked: ["map-maker", "bug-finder"] as BadgeId[],
};

export const libraries = [
  { name: "xcore-ui", repo: "xcore-ui", coords: "org.xcore:xcore-ui:0.1.0" },
  { name: "xcore-plugin-template", repo: "xcore-plugin-template" },
  { name: "mindustry-testkit", repo: "mindustry-testkit" },
  { name: "cloud-mindustry", repo: "cloud-mindustry" },
  { name: "FluBundle", repo: "FluBundle" },
  { name: "fluent", repo: "fluent" },
  { name: "xcore-protocol", repo: "xcore-protocol" },
  { name: "TileLogger", repo: "TileLogger" },
] as const;
