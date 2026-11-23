#!/usr/bin/env node
// One-off backfill for the per-puzzle solve counter (#2032): sets puzzles/{code}.solves to the server count() of
// puzzles/{code}/entries. The Admin SDK skips the rules, which let a client move `solves` only by +1 beside its
// own first entry, so a recount can never run on a client.
//
// Order: deploy the rules, run this once. Every current client bumps beside its first entry, so the count()-then-write
// gap can drop a live bump on a rerun (a puzzle whose stored count already matches is skipped); never rerun it.
//
// Dry run by default; --write applies. The project id is required, never inferred:
//   npm i --no-save firebase-admin
//   GOOGLE_APPLICATION_CREDENTIALS=<service-account.json> node backfill-solves.mjs --project <id> [--write]

import { applicationDefault, initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';

const args = process.argv.slice(2);
const projectAt = args.indexOf('--project');
const projectId = projectAt >= 0 ? args[projectAt + 1] : undefined;
const write = args.includes('--write');

if (!projectId || projectId.startsWith('--')) {
  console.error('usage: node backfill-solves.mjs --project <id> [--write]');
  process.exit(2);
}

initializeApp({ credential: applicationDefault(), projectId });
const db = getFirestore();

const BATCH_LIMIT = 400;
let batch = db.batch();
let pending = 0;
let scanned = 0;
let changed = 0;

const puzzles = await db.collection('puzzles').select('solves').get();
for (const puzzle of puzzles.docs) {
  scanned++;
  const count = (await puzzle.ref.collection('entries').count().get()).data().count;
  const stored = puzzle.get('solves');
  if (stored === count) continue;
  changed++;
  console.log(`${puzzle.id}: ${stored ?? '(absent)'} -> ${count}`);
  if (!write) continue;
  batch.update(puzzle.ref, { solves: count });
  if (++pending >= BATCH_LIMIT) {
    await batch.commit();
    batch = db.batch();
    pending = 0;
  }
}
if (write && pending > 0) await batch.commit();

console.log(`${projectId}: ${scanned} puzzles scanned, ${changed} ${write ? 'updated' : 'would change (dry run)'}`);
