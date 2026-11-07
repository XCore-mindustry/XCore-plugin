import { expect, test } from "bun:test";
import fixture from "../../plugins/xcore/src/test/resources/fixtures/player-totals.v1.json";
import { overviewFromTotals } from "../src/lib/server/player-totals";

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
