package com.ivandsky.puzzlebox.play.domain.nextthing

import com.ivandsky.puzzlebox.play.domain.moments.friendNeighbours

/**
 * Rung 2, and Result's Challenge row (L4): the friend just above the player on today's Friends board, else the
 * friend just below. Only that one friend's tier is read, once, and the offer is made only when it allows a
 * challenge (ADR-0031 §15); a failed read offers nothing, and no other friend is tried instead.
 */
internal object ChallengeFriendNextThingProvider : NextThingProvider {
    override val order = 20

    override suspend fun offer(facts: NextThingFacts): NextThing.ChallengeFriend? {
        val viewer = facts.viewerUid ?: return null
        val friend = facts.friendBoard()?.friendNeighbours(viewer)?.nearest ?: return null
        val challengeable = facts.friendTier(friend.uid)?.allowsChallenge == true
        return if (challengeable) NextThing.ChallengeFriend(friend.uid, friend.name) else null
    }
}
