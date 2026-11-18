package com.ivandsky.puzzlebox.play.random

import com.ivandsky.puzzlebox.domain.random.RandomPick

/**
 * The last random pick, so the next pick can avoid it. Process memory only: a restart forgets it (spec A5). It is here and
 * not in :core:domain because a stateful class there breaks the domain structure checks.
 */
internal class RandomPickMemory {
    var last: RandomPick? = null
}
