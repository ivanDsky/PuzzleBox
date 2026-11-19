package com.ivandsky.puzzlebox.play.domain.nextthing

/**
 * Rung 4b: the newest community puzzle the player has not opened. Opening a puzzle keeps it among the received
 * ones whatever became of it, so any code there is opened. The list is newest first and already without the
 * player's own and hidden puzzles; it is empty while the listing cannot answer.
 */
internal object CommunityNextThingProvider : NextThingProvider {
    override val order = 45

    override suspend fun offer(facts: NextThingFacts): NextThing? {
        val opened = facts.friendPuzzles.mapTo(HashSet()) { it.code }
        return facts.communityPuzzles().firstOrNull { it.code !in opened }
    }
}
