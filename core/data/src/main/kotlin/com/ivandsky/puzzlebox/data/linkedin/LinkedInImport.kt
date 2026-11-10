package com.ivandsky.puzzlebox.data.linkedin

import com.ivandsky.puzzlebox.data.model.PuzzleRefKeys
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.CompletionSource
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.game.linkedin.LinkedInOwnResult
import com.ivandsky.puzzlebox.game.linkedin.linkedInScore
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate

/**
 * How the player's own LinkedIn result becomes a [CompletionRecord]. Pure: the caller has already
 * resolved the game's LinkedIn type and fetched the result, and never asks for a record of a day the
 * player did not play.
 */
internal object LinkedInImport {

    /**
     * The ref carries the LinkedIn edition number, so it shares one key with a PuzzleBox play of the same
     * daily and both fall under the same first-win and dedupe rules. Time and hints come from the shared
     * LinkedIn score mapping (LinkedIn reports whole seconds, hints as at least one, never a count). The
     * store stamps first-completed and record flags and the import instant; here they stay unset.
     * Null when [game] is not in [registry]: nothing is to be written then.
     */
    fun toRecord(
        game: GameId,
        day: LocalDate,
        puzzleNumber: Int,
        solved: LinkedInOwnResult.Solved,
        registry: GameRegistry,
    ): CompletionRecord? {
        val ref = PuzzleRefKeys.decode("${game.value}/$day/$puzzleNumber", registry) ?: return null
        val score = linkedInScore(solved.timeSeconds * 1000L, solved.hintFree)
        return CompletionRecord(
            puzzleRef = ref,
            time = score.timeMs,
            hints = score.hints,
            stats = RecordStats(1, "{}"),
            source = CompletionSource.LinkedIn,
        )
    }
}
