package com.ivandsky.puzzlebox.domain.lineup

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
import com.ivandsky.puzzlebox.domain.OpenOutcome
import com.ivandsky.puzzlebox.model.ShareCode
import kotlinx.coroutines.withContext

/**
 * The add sheet's code lookup: a share code or a `/p/` link opened through [load] (`LoadSharedPuzzle`), which keeps a
 * puzzle it found among the player's friends' puzzles as Enter code does (A4). No network path of its own; offline is
 * its own answer and never reads as a code nobody shared (A20). [load] runs on the default dispatcher, as its CPU
 * check asks.
 */
class LoadPuzzleByCode(
    private val load: suspend (ShareCode) -> OpenOutcome,
    private val dispatchers: DispatcherProvider,
) {
    suspend operator fun invoke(text: String): CodeLookup {
        val code = codeIn(text) ?: return CodeLookup.Invalid
        return when (val outcome = withContext(dispatchers.default) { load(code) }) {
            is OpenOutcome.Opened -> CodeLookup.Found(outcome.puzzle.picked())
            OpenOutcome.NotFound -> CodeLookup.NotFound
            OpenOutcome.NoLongerAvailable -> CodeLookup.NoLongerAvailable
            OpenOutcome.Offline -> CodeLookup.Offline
            is OpenOutcome.NeedsUpdate, is OpenOutcome.UnknownGame -> CodeLookup.CannotOpen
            OpenOutcome.FailedCheck -> CodeLookup.CheckFailed
        }
    }

    companion object {
        /** The code in [text]: a code in any case, with or without the dash, or a puzzle link; null for anything else. */
        fun codeIn(text: String): ShareCode? = ShareCode.parse(text) ?: ShareCode.fromLink(text.trim())

        /**
         * The code a typed [text] announces on its own: a puzzle link or the dashed share form. A bare six letters is as likely a
         * word ("dragon") as a code, so it is looked up only when the player pastes it or asks to search.
         */
        fun announcedIn(text: String): ShareCode? =
            ShareCode.fromLink(text.trim()) ?: text.trim().takeIf { DASHED.matches(it) }?.let(ShareCode::parse)

        private val DASHED = Regex("[A-Za-z0-9]{3}-[A-Za-z0-9]{3}")
    }
}

private fun ReceivedPuzzle.picked() = PickablePuzzle(
    entry = ExactPuzzle(LegRef.Custom(gameId, code), authorUid, title, authorName, PuzzleSource.Friend),
    isNew = false,
    played = false,
    meta = metaOf(variant.value),
    code = code,
    level = difficulty,
)
