package com.ivandsky.puzzlebox.data.cloud

/**
 * The kinds this process syncs, in the order a run visits them: the one list the engine, the runner and the erase all
 * read. It is built once per process, so a kind that joins only while a release flag is on (the trainer kind) is in or
 * out for every consumer alike, and a flag flipped later takes effect on the next start.
 */
class SyncedKinds(val all: List<SyncedKind>)
