package com.ivandsky.puzzlebox.play.domain.nextthing

import com.ivandsky.puzzlebox.play.domain.FriendStatus

/** Rung 4: the first received friend puzzle that was never played. */
internal object NewFriendPuzzleNextThingProvider : NextThingProvider {
    override val order = 40

    override suspend fun offer(facts: NextThingFacts): NextThing? =
        facts.friendPuzzles.firstOrNull { it.status == FriendStatus.New }
            ?.let { NextThing.FriendPuzzle(it.code, it.gameId, it.authorName) }
}
