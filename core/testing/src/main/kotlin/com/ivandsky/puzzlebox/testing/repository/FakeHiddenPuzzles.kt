package com.ivandsky.puzzlebox.testing.repository

import com.ivandsky.puzzlebox.data.visibility.HiddenPuzzles
import com.ivandsky.puzzlebox.model.ShareCode

/** In-memory [HiddenPuzzles]: [hidden] is the persisted state, so a second use case over the same instance stands in for a relaunch. */
class FakeHiddenPuzzles : HiddenPuzzles {

    val hidden = mutableSetOf<ShareCode>()

    override suspend fun codes(): Set<ShareCode> = hidden.toSet()

    override suspend fun add(code: ShareCode) {
        hidden += code
    }
}
