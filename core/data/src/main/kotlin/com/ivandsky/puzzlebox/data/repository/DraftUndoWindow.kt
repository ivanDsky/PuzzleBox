package com.ivandsky.puzzlebox.data.repository

/** How long a deleted draft can be undone; its cloud tombstone waits this long (plus a margin) before it may upload. */
const val DRAFT_UNDO_WINDOW_MS = 5_000L
