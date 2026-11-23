package com.ivandsky.puzzlebox.data.cloud

/**
 * The kinds this process syncs, in the order a run visits them: the one list the engine, the runner and the erase all
 * read. It is built once per process, so every consumer sees the same kinds.
 */
class SyncedKinds(val all: List<SyncedKind>)
