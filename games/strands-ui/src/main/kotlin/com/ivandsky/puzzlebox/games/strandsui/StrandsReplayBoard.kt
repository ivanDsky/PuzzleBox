package com.ivandsky.puzzlebox.games.strandsui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.game.replay.ReplayFrame
import com.ivandsky.puzzlebox.gameui.replay.GameReplayBoard
import com.ivandsky.puzzlebox.gameui.replay.ReplayBoardOptions
import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strands.StrandsPuzzle
import com.ivandsky.puzzlebox.games.strands.StrandsSession
import com.ivandsky.puzzlebox.games.strands.StrandsState
import com.ivandsky.puzzlebox.games.strands.engine.StrandsVerdict
import com.ivandsky.puzzlebox.games.strands.render.StrandsRendererModel
import com.ivandsky.puzzlebox.games.strandsui.chrome.WordSlotLine

/**
 * Strands' read-only replay board: the committed board and the marks live play derives from it
 * (found words, spangram, hint outline), drawn by the live board. No trace, verdict or input. A
 * frame that found one more word than [ReplayFrame.before] plays the live settle or link sweep
 * while the replay plays ([ReplayBoardOptions.animate]); paused or scrubbing, every frame rests.
 *
 * Above the board sits live play's word slot: a frame that submitted a word (a theme word, the
 * spangram, or a non-theme word, which marks no cell) shows that word and its verdict until the
 * next move; any other frame leaves the line empty at the same height, so the board never moves.
 *
 * [ReplayFrame.hinted] holds the cells of each hinted word as `row * cols + col`
 * ([com.ivandsky.puzzlebox.games.strands.StrandsReplay.markOf]); the board puts the hint badge on
 * the first letter of each such word. A hinted word is always either found or the active hint, so
 * the badge never outlives the word's marks.
 */
internal object StrandsReplayBoard : GameReplayBoard {
    override val pieces: String get() = "words"

    @Composable
    override fun Board(frame: ReplayFrame, options: ReplayBoardOptions, modifier: Modifier) {
        // The frame is game-blind; this board is only reached through Strands' own plugin, so a miss draws nothing.
        val session = frame.session as? StrandsSession ?: return
        val before = (frame.before as? StrandsSession)?.state
        val model = remember(session.state) { StrandsRendererModel.of(session.state) }
        val event = remember(session.state, before) { foundEventOf(before, session.state) }
        // A word that was shown at rest (paused, scrubbed) must not play when the replay resumes on the same frame.
        var rested by remember { mutableIntStateOf(-1) }
        SideEffect { if (!options.animate) rested = event?.seq ?: -1 }
        val badges = remember(session.state.puzzle, frame.hinted) { hintBadges(session.state.puzzle, frame.hinted) }
        val slot = remember(session.state, before) { submittedSlotOf(before, session.state) }
        Column(modifier) {
            WordSlotLine(slot, model.foundCount, model.totalCount, reduceMotion = !options.animate)
            StrandsBoardContent(
                model = model,
                found = event?.takeIf { options.animate && it.seq != rested },
                reduceMotion = !options.animate,
                trace = { Trace() },
                onTrace = {},
                // Nothing to touch, so the board shrinks to whatever space it is given.
                floor = 0.dp,
                readOnly = true,
                hintBadges = badges,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** The word [after] found on top of [before] (one more, the newest), as live play would announce it; null for any other step. */
private fun foundEventOf(before: StrandsState?, after: StrandsState): FoundEvent? {
    if (before == null || after.found.size != before.found.size + 1) return null
    val puzzle = after.puzzle
    val text = after.found.last()
    val spangram = text == puzzle.spangram.text
    val word = if (spangram) puzzle.spangram else puzzle.themeWords.firstOrNull { it.text == text } ?: return null
    return FoundEvent(word.cells, spangram, seq = after.found.size)
}

/**
 * The word slot live play shows after the submit that took [before] to [after]: the one new found word, or the one new
 * non-theme word, read as live reads it. Empty for any other step (a hint) and for the first frame.
 */
internal fun submittedSlotOf(before: StrandsState?, after: StrandsState): WordSlot {
    if (before == null) return WordSlot.Empty
    val puzzle = after.puzzle
    val verdict = when {
        after.found.size > before.found.size -> after.found.last().let { text ->
            if (text == puzzle.spangram.text) StrandsVerdict.Spangram(puzzle.spangram)
            else puzzle.themeWords.firstOrNull { it.text == text }?.let(StrandsVerdict::ThemeWord)
        }
        after.nonTheme.size > before.nonTheme.size -> StrandsVerdict.NonTheme(after.nonTheme.last())
        else -> null
    } ?: return WordSlot.Empty
    return StrandsVerdictSlot.of(verdict, seq = after.found.size + after.nonTheme.size)
}

/** The first letter cell of every word whose cells [hinted] names. */
private fun hintBadges(puzzle: StrandsPuzzle, hinted: Set<Int>): Set<Coord> {
    if (hinted.isEmpty()) return emptySet()
    return (puzzle.themeWords + puzzle.spangram)
        .filter { word -> word.cells.first().let { it.row * puzzle.cols + it.col } in hinted }
        .mapTo(HashSet()) { it.cells.first() }
}
