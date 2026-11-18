package com.ivandsky.puzzlebox.data.linkedin

import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.CompletionSource
import com.ivandsky.puzzlebox.game.linkedin.LinkedInPlay
import com.ivandsky.puzzlebox.game.linkedin.LinkedInPost
import com.ivandsky.puzzlebox.model.PuzzleKey

/**
 * Whether one game day can be posted. The button and the submit guard both ask here, so a button never
 * offers what a tap would refuse. Pure: it reads nothing itself, and the caller supplies the replay's
 * encoding only on demand.
 */
internal object LinkedInPostEligibility {

    /** What the phone holds about one game day. [record] is the day's record row, [delta] its age in days. */
    data class Facts(
        val posting: Boolean,
        val link: LinkState,
        val record: CompletionRecord?,
        val typeId: Int?,
        val gamePath: String?,
        val delta: Int?,
        val posted: Boolean,
        val sending: Boolean,
        val already: Boolean = false,
        /** LinkedIn's result for the day is already on the phone: the day sync imported the player's own solved result. */
        val onLinkedIn: Boolean = false,
    )

    sealed interface Verdict {
        /** Nothing may be sent; [state] is why. */
        data class Blocked(val state: LinkedInPostState) : Verdict

        /** [post] may be sent as [profileId]. [unsure]: an earlier send has no known outcome. */
        data class Open(val profileId: String, val post: LinkedInPost, val unsure: Boolean) : Verdict
    }

    /**
     * The order is the contract. What can never be posted is hidden first, so posting off or an import hides
     * even a posted or paused day. A posted day says so on any later day, and so does a day LinkedIn said it already holds. A day that is not today is refused
     * before [play] is asked. A day whose own LinkedIn result the phone already read is [Facts.onLinkedIn]: it is told as already there
     * only where a post could otherwise go out, so a pause or a missing replay still speaks first.
     */
    suspend fun decide(facts: Facts, play: suspend () -> LinkedInPlay?): Verdict {
        if (!facts.posting) return HIDDEN
        val profileId = when (val link = facts.link) {
            LinkState.NotConnected -> return HIDDEN
            is LinkState.Connected -> link.profileId
            is LinkState.Paused -> link.profileId
        }
        // The record row is the first solve of the day, so an import or a duel leg that won it hides a later replay too.
        val record = facts.record?.takeIf { it.source == CompletionSource.App && it.matchCode == null } ?: return HIDDEN
        val number = (record.puzzleRef.key as? PuzzleKey.Daily)?.puzzleNumber ?: return HIDDEN
        val typeId = facts.typeId ?: return HIDDEN
        val gamePath = facts.gamePath ?: return HIDDEN
        if (facts.posted) return Verdict.Blocked(LinkedInPostState.Posted)
        if (facts.already) return Verdict.Blocked(LinkedInPostState.AlreadyOnLinkedIn)
        if (facts.delta != 0) return Verdict.Blocked(LinkedInPostState.NotToday)
        val encoded = play() ?: return HIDDEN
        if (facts.link is LinkState.Paused) return Verdict.Blocked(LinkedInPostState.Paused)
        if (facts.onLinkedIn) return Verdict.Blocked(LinkedInPostState.AlreadyOnLinkedIn)
        val post = LinkedInPost(gamePath, typeId, number, record.time, hintUsed = record.hints > 0, play = encoded)
        return Verdict.Open(profileId, post, unsure = facts.sending)
    }

    private val HIDDEN = Verdict.Blocked(LinkedInPostState.Hidden)
}
