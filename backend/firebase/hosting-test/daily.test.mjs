import { readFileSync } from "node:fs";
import test from "node:test";
import assert from "node:assert/strict";
import { parseDaily, headingFor, dateText } from "../hosting/p/daily.js";

const vectors = JSON.parse(readFileSync(new URL("./daily-link-vectors.json", import.meta.url), "utf8"));

test("every vector parses to its expected game and day", () => {
  for (const { path, game, day } of vectors) {
    const expected = game === null ? null : { game, day };
    assert.deepEqual(parseDaily(path), expected, JSON.stringify(path));
  }
});

test("the five shipped games get a named heading", () => {
  assert.equal(headingFor("queens"), "PuzzleBox · Queens");
  assert.equal(headingFor("tango"), "PuzzleBox · Tango");
  assert.equal(headingFor("zip"), "PuzzleBox · Zip");
  assert.equal(headingFor("minisudoku"), "PuzzleBox · Mini Sudoku");
  assert.equal(headingFor("strands"), "PuzzleBox · Strands");
});

test("an unknown game keeps the default heading", () => {
  assert.equal(headingFor("sudoku"), "A PuzzleBox daily puzzle");
  assert.equal(headingFor("newgame9"), "A PuzzleBox daily puzzle");
});

test("the date reads like Nov 18, 2026 with no locale lookup", () => {
  assert.equal(dateText("2026-11-18"), "Nov 18, 2026");
  assert.equal(dateText("2026-01-05"), "Jan 5, 2026");
  assert.equal(dateText("2028-02-29"), "Feb 29, 2028");
  assert.equal(dateText("2026-12-31"), "Dec 31, 2026");
});
