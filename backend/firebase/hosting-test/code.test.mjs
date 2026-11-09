import { readFileSync } from "node:fs";
import test from "node:test";
import assert from "node:assert/strict";
import { normalize } from "../hosting/p/code.js";
import { normalize as normalizeMatch } from "../hosting/m/code.js";

const vectors = JSON.parse(readFileSync(new URL("./share-code-vectors.json", import.meta.url), "utf8"));

test("every vector normalises to its expected code", () => {
  for (const { input, code } of vectors) {
    const result = normalize(input);
    if (code === null) {
      assert.equal(result, null, `expected null for ${JSON.stringify(input)}, got ${JSON.stringify(result)}`);
    } else {
      assert.deepEqual(result, { code, display: `${code.slice(0, 3)}-${code.slice(3)}` });
    }
  }
});

test("pathname stripping: /p/k7q2mx displays K7Q-2MX", () => {
  assert.deepEqual(normalize("/p/k7q2mx"), { code: "K7Q2MX", display: "K7Q-2MX" });
});

test("pathname stripping: /p/K7Q-2MX/ keeps the dash and trailing slash out", () => {
  assert.deepEqual(normalize("/p/K7Q-2MX/"), { code: "K7Q2MX", display: "K7Q-2MX" });
});

test("pathname stripping: /p/ gives the invalid state", () => {
  assert.equal(normalize("/p/"), null);
});

test("pathname stripping: /p/K7Q2M gives the invalid state", () => {
  assert.equal(normalize("/p/K7Q2M"), null);
});

test("/m/ pathname: /m/k7q2mx displays K7Q-2MX", () => {
  assert.deepEqual(normalizeMatch("/m/k7q2mx"), { code: "K7Q2MX", display: "K7Q-2MX" });
});

test("/m/ pathname: /m/K7Q-2MX/ keeps the dash and trailing slash out", () => {
  assert.deepEqual(normalizeMatch("/m/K7Q-2MX/"), { code: "K7Q2MX", display: "K7Q-2MX" });
});

test("/m/ pathname: /m/ and /m/K7Q2M give the invalid state", () => {
  assert.equal(normalizeMatch("/m/"), null);
  assert.equal(normalizeMatch("/m/K7Q2M"), null);
});

test("/m/ normalize rejects a /p/ path", () => {
  assert.equal(normalizeMatch("/p/K7Q2MX"), null);
});
