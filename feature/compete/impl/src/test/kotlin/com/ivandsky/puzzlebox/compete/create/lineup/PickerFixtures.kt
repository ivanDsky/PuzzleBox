package com.ivandsky.puzzlebox.compete.create.lineup

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.compete.create.QUEENS
import com.ivandsky.puzzlebox.compete.create.TANGO
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.domain.lineup.CodeLookup
import com.ivandsky.puzzlebox.domain.lineup.ExactPuzzle
import com.ivandsky.puzzlebox.domain.lineup.Freshness
import com.ivandsky.puzzlebox.domain.lineup.PickablePuzzle
import com.ivandsky.puzzlebox.domain.lineup.PickerQuery
import com.ivandsky.puzzlebox.domain.lineup.PickerResult
import com.ivandsky.puzzlebox.domain.lineup.PuzzleSource
import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal fun pickable(game: GameId, code: String, title: String, author: String?, source: PuzzleSource, isNew: Boolean = false, played: Boolean = false, level: Difficulty.Level? = null): PickablePuzzle {
    val share = requireNotNull(ShareCode.parse(code))
    return PickablePuzzle(ExactPuzzle(LegRef.Custom(game, share), "uid-$code", title, author, source), isNew, played, meta = "n8", code = share, level = level)
}

internal val CROWN_JEWELS = pickable(QUEENS, "K7Q2MX", "Crown jewels", "Bo", PuzzleSource.Friend, isNew = true)
internal val CROWN_TANGO = pickable(TANGO, "M3N4P5", "Crown Tango", "Cy", PuzzleSource.Community, isNew = true)
internal val OLD_CROWN = pickable(QUEENS, "R6S7T8", "Old crown", "Di", PuzzleSource.Community)
internal val MY_GRID = pickable(QUEENS, "V9W2X3", "My grid", null, PuzzleSource.Mine, played = true)

/** A lookup for the tests that never paste a code: it finds nothing. */
internal val NO_LOOKUP: suspend (String) -> CodeLookup = { CodeLookup.Invalid }

/** The pickable list without the domain's own filtering: [rows] kept when text, chips and games agree, in the order given. */
internal fun pickerOf(rows: List<PickablePuzzle>, offline: Boolean = false): (Flow<PickerQuery>, Flow<Int>) -> Flow<PickerResult> = { queries, _ ->
    queries.map { q ->
        PickerResult(
            rows.filter { row ->
                val text = q.text.trim()
                (text.isEmpty() || listOfNotNull(row.entry.title, row.entry.author, row.code?.display, row.code?.value).any { it.contains(text, ignoreCase = true) }) &&
                    (q.fresh.isEmpty() || Freshness.New in q.fresh && row.isNew || Freshness.NotPlayed in q.fresh && !row.played) &&
                    (q.sources.isEmpty() || row.entry.source in q.sources) &&
                    (q.games.isEmpty() || row.entry.gameId in q.games)
            },
            offline,
        )
    }
}

/** Opens the chip of [page] as a page of the Add puzzle sheet, taps its option [rows], and goes back to the picker. */
internal fun ComposeTestRule.pickInPage(page: PickerFilterPage, vararg rows: String) {
    tapAndSettle(page.tag)
    rows.forEach { tapAndSettle(it) }
    tapAndSettle("${page.tag}.page.back")
}

/** Opens the chip of [page] as its own sheet over the page, taps its option [rows], and closes the sheet. */
internal fun ComposeTestRule.pickInSheet(page: PickerFilterPage, vararg rows: String) {
    tapAndSettle(page.tag)
    rows.forEach { tapAndSettle(it) }
    tapAndSettle("${page.tag}.sheet.close")
}

private fun ComposeTestRule.tapAndSettle(tag: String) {
    onNodeWithTag(tag).performClick()
    waitForIdle()
}
