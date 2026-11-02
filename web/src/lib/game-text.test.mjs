// node --test src/lib/game-text.test.mjs
import assert from "node:assert/strict";
import { test } from "node:test";
import { parseGameText } from "./game-text.ts";

test("short hex fills RRGGBB from the left, like Arc", () => {
  assert.deepEqual(parseGameText("[#3]G[#61]ud"), [
    { text: "G", color: "#300000ff" },
    { text: "ud", color: "#610000ff" },
  ]);
});

test("named colours, pops and escapes", () => {
  assert.deepEqual(parseGameText("a[red]b[]c[[d[LIGHT_GRAY]e"), [
    { text: "a", color: null },
    { text: "b", color: "#e55454" },
    { text: "c[d", color: null },
    { text: "e", color: "#bfbfbf" },
  ]);
});

test("tags the game does not know stay as text", () => {
  assert.deepEqual(parseGameText("[Red]x[#1234567]y[]z[unclosed"), [
    { text: "[Red]x[#1234567]y", color: null },
    { text: "z[unclosed", color: null },
  ]);
  assert.deepEqual(parseGameText("[#FFFFFF00]hidden"), [{ text: "hidden", color: "#ffffff00" }]);
});
