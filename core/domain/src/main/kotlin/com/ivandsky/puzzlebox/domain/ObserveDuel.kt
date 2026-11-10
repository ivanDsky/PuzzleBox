package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.match.Match
import com.ivandsky.puzzlebox.data.model.PuzzleRefKeys
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.SavedPlayRepository
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.model.ShareCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * The duel screen's read model for one code: [ObserveMatch], the viewer's completions (for [DuelSnapshot.solvedBefore])
 * and the viewer's saved plays of this duel ([DuelSnapshot.elapsedMs]) as one flow. Writes nothing.
 */
class ObserveDuel(
    private val observeMatch: ObserveMatch,
    private val completions: CompletionRepository,
    private val savedPlays: SavedPlayRepository,
) {

    operator fun invoke(code: ShareCode): Flow<DuelSnapshot> {
        val solved = completions.observeRecords().map { rows -> rows.mapTo(HashSet()) { PuzzleRefKeys.encode(it.puzzleRef) } }
        val playing = savedPlays.observeInProgress().map { slots ->
            slots.mapNotNull { (slot, elapsed) ->
                (slot as? PlaySlot.Leg)?.takeIf { it.matchCode == code.value }?.let { it.legIndex to elapsed }
            }.toMap()
        }
        return combine(observeMatch(code), solved, playing) { match, done, elapsed ->
            DuelSnapshot(match, solvedBefore = match.read()?.legs.orEmpty().any { it.ref.wire in done }, elapsedMs = elapsed)
        }
    }

    /** The match document the state carries, when it carries one. */
    private fun MatchState.read(): Match? = when (this) {
        is MatchState.Loaded -> view.match
        is MatchState.SignedOut -> match
        MatchState.NotFound, MatchState.Offline, is MatchState.UpdateApp -> null
    }
}
