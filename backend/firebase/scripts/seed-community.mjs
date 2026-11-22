#!/usr/bin/env node
// Dev-only (#2066): publishes the notation files in seed-community/ as public community puzzles, so the Community
// shelves have fresh cards to show. Not part of the app. The writes are admin writes: they use the firebase-tools
// login (`firebase login`) over the Firestore REST API, which skips firestore.rules, so the seed author is the fake
// uid below, not a signed-in user. Nothing is deleted or cleaned up afterwards.
//
// Needs the built CLI:  ./gradlew :tools:puzzle-cli:installDist
// Dry run by default; --write applies. The project id is required, never inferred:
//   node seed-community.mjs --project <id> [--write]

import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const cli = join(here, '../../../tools/puzzle-cli/build/install/puzzle-cli/bin/puzzle-cli');
const args = process.argv.slice(2);
const at = args.indexOf('--project');
const project = at >= 0 ? args[at + 1] : undefined;
const write = args.includes('--write');
if (!project || project.startsWith('--')) {
  console.error('usage: node seed-community.mjs --project <id> [--write]');
  process.exit(2);
}

const AUTHOR_UID = 'puzzlebox-test-author';
const AUTHOR_NAME = 'PuzzleBox test';
const NOTE = 'Seed puzzle for checking the Community shelves.';
const CODE_CHARS = '0123456789ABCDEFGHJKMNPQRSTVWXYZ';
// file, title, authorTimeMs
const SEEDS = [
  ['queens1', 'Seed · Queens 6×6', 70000],
  ['queens2', 'Seed · Queens 7×7', 120000],
  ['queens3', 'Seed · Queens 8×8', 210000],
  ['zip1', 'Seed · Zip 5×5', 45000],
  ['zip2', 'Seed · Zip 6×6', 95000],
  ['zip3', 'Seed · Zip 7×7', 160000],
  ['tango1', 'Seed · Tango A', 150000],
  ['tango2', 'Seed · Tango B', 180000],
  ['mini1', 'Seed · Mini Sudoku A', 140000],
  ['mini2', 'Seed · Mini Sudoku B', 200000],
  ['strands1', 'Seed · Strands: beach', 260000],
  ['strands2', 'Seed · Strands: breakfast', 300000],
];

function accessToken() {
  const read = () => JSON.parse(readFileSync(join(homedir(), '.config/configstore/firebase-tools.json'), 'utf8')).tokens;
  let tokens = read();
  if (!tokens.expires_at || tokens.expires_at < Date.now() + 60_000) {
    execFileSync('firebase', ['projects:list', '--json'], { stdio: 'ignore' }); // refreshes the stored token
    tokens = read();
  }
  return tokens.access_token;
}

const randomCode = () => Array.from({ length: 6 }, () => CODE_CHARS[Math.floor(Math.random() * 32)]).join('');
const str = (s) => ({ stringValue: s });
const int = (n) => ({ integerValue: String(n) });

const now = Date.now();
const docs = SEEDS.map(([file, title, timeMs], i) => {
  const line = JSON.parse(
    execFileSync(cli, ['share', join(here, 'seed-community', `${file}.txt`)], { encoding: 'utf8' }),
  );
  const fields = {
    formatVersion: int(1),
    gameId: str(line.game),
    variant: str(line.variant),
    shareCodecVersion: int(line.shareCodecVersion),
    payload: str(line.payload),
    title: str(title),
    authorName: str(AUTHOR_NAME),
    note: str(NOTE),
    authorUid: str(AUTHOR_UID),
    authorTimeMs: int(timeMs),
    authorHints: int(0),
    createdAt: { timestampValue: new Date(now - i * 60_000).toISOString() },
    unpublished: { booleanValue: false },
    visibility: str('public'),
  };
  if (line.difficulty) fields.difficulty = str(line.difficulty);
  return { title, game: line.game, fields };
});

if (!write) {
  for (const d of docs) console.log('would publish', d.game, d.title);
  process.exit(0);
}

const token = accessToken();
const base = `https://firestore.googleapis.com/v1/projects/${project}/databases/(default)/documents/puzzles`;
for (const d of docs) {
  for (let attempt = 0; attempt < 5; attempt++) {
    const code = randomCode();
    // create fails with 409 on a taken code, so a seed never overwrites a puzzle
    const res = await fetch(`${base}?documentId=${code}`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({ fields: d.fields }),
    });
    if (res.ok) {
      console.log(code, d.game, d.title);
      break;
    }
    if (res.status !== 409 || attempt === 4) {
      console.error('failed', d.title, res.status, await res.text());
      process.exit(1);
    }
  }
}
