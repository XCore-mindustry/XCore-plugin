import { expect, test } from "bun:test";
import fixture from "../../plugins/xcore/src/test/resources/fixtures/player-totals.v1.json";
import { overviewFromTotals, readTotals } from "../src/lib/server/player-totals";

test("reads the shared Java/Python/TS projection without losing long counters", () => {
  const overview = overviewFromTotals(fixture);
  expect(overview.overall.gamesPlayed).toBe(8);
  expect(overview.overall.blocksBuilt).toBe(3_000_000_000);
  expect(overview.survival.gamesPlayed - (overview.survival.left ?? 0)).toBe(1);
  expect(overview.hexed.bestPlacement).toBe(1);
  expect(overview.updating).toBe(false);
});
test("distinguishes newcomers, pending, empty, stale, unsupported and unsafe documents", () => {
  expect(overviewFromTotals(null)).toMatchObject({ status: "ready", updating: false, overall: { gamesPlayed: 0 } });
  expect(overviewFromTotals({ schema_version: 1, status: "pending" })).toMatchObject({ status: "pending", updating: true });
  expect(overviewFromTotals({ schema_version: 1, status: "ready" }).status).toBe("ready");
  expect(overviewFromTotals({ ...fixture, rebuild: { dirty: true } }).updating).toBe(true);
  expect(() => overviewFromTotals({ ...fixture, schema_version: 99 })).toThrow();
  expect(() => overviewFromTotals({ ...fixture, all: { games: BigInt("9007199254740992") } })).toThrow();
  expect(JSON.stringify(overviewFromTotals(fixture))).not.toContain("fixture-player");
});

test("pending records also tolerate the migration's partial document", () => {
  expect(overviewFromTotals({ _id: "new", schema_version: 1, status: "pending" }).overall.gamesPlayed).toBe(0);
});
test("follows merge redirects and stays pending until the backfill is done", async () => {
  const docs: Record<string, Record<string, unknown>> = {
    a: { _id: "a", schema_version: 1, status: "ready", redirect_uuid: "b" },
    b: { _id: "b", schema_version: 1, status: "ready", redirect_uuid: "c" },
    c: { _id: "c", schema_version: 1, status: "ready", all: { games: 9 } },
  };
  const find = async (uuid: string) => docs[uuid] ?? null;
  expect(overviewFromTotals(await readTotals(find, async () => true, "a")).overall.gamesPlayed).toBe(9);
  expect(await readTotals(find, async () => true, "newcomer")).toBeNull();
  expect(overviewFromTotals(await readTotals(find, async () => false, "old-player")).status).toBe("pending");
  docs.c.redirect_uuid = "a";
  await expect(readTotals(find, async () => true, "a")).rejects.toThrow();
});
