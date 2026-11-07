import type { Document } from "mongodb";
import type { PlayerAggregateOverview, ModeStatsSummaryDTO } from "./stats-types";

/** Reject precision loss at the BSON/JSON boundary rather than silently changing a counter. */
function count(row: Document, key: string): number {
  const value = row[key] == null ? 0 : Number(row[key]);
  if (!Number.isSafeInteger(value) || value < 0) throw new Error(`Invalid player_totals counter: ${key}`);
  return value;
}
function mode(row: Document = {}): ModeStatsSummaryDTO {
  const waveN = count(row, "wave_n");
  return {
    gamesPlayed: count(row, "games"), gamesWon: count(row, "wins"),
    bestWave: count(row, "best_wave"),
    averageWave: waveN ? Math.round(count(row, "wave_sum") / waveN) : 0,
    bestPlacement: count(row, "best_place"), top3Finishes: count(row, "top3"),
    left: count(row, "left"), streak: count(row, "streak"),
    bestStreak: count(row, "best_streak"), playMs: count(row, "play_ms"),
  };
}
export function overviewFromTotals(doc: Document | null): PlayerAggregateOverview {
  if (doc && doc.schema_version !== 1) throw new Error("Unsupported player_totals schema");
  // readTotals answers pending while the backfill runs, so here a missing document means no games yet.
  const ready = doc === null || doc.status === "ready";
  const all = ready ? (doc?.all ?? {}) : {};
  const modes = ready ? (doc?.modes ?? {}) : {};
  return {
    status: ready ? "ready" : "pending", updating: !ready || Boolean(doc?.rebuild?.dirty),
    overall: {
      gamesPlayed: count(all, "games"), gamesWon: count(all, "wins"),
      blocksBuilt: count(all, "blocks_built"), blocksDeconstructed: count(all, "blocks_deconstructed"),
      blocksDestroyed: 0, unitsProduced: 0, unitsDestroyed: 0,
      left: count(all, "left"), streak: count(all, "streak"),
    },
    pvp: mode(modes.PVP), survival: mode(modes.SURVIVAL), hexed: mode(modes.HEXED),
  };
}

/** The surviving account's totals after merges, and pending until the first backfill has enrolled everyone. */
export async function readTotals(
  findTotals: (uuid: string) => Promise<Document | null>,
  backfillDone: () => Promise<boolean>,
  uuid: string,
): Promise<Document | null> {
  const seen = new Set<string>();
  let doc = await findTotals(uuid);
  while (doc && typeof doc.redirect_uuid === "string" && doc.redirect_uuid) {
    if (seen.has(uuid)) throw new Error("Cyclic player_totals redirect");
    seen.add(uuid);
    uuid = doc.redirect_uuid;
    doc = await findTotals(uuid);
  }
  if (doc) return doc;
  // Every stored game upserts its owners, so once the backfill is done no document means no games yet.
  return (await backfillDone()) ? null : { _id: uuid, schema_version: 1, status: "pending" };
}
