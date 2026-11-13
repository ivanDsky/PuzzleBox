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
import com.ivandsky.puzzlebox.game.replay.ReplayMove
import com.ivandsky.puzzlebox.gameui.replay.GameReplayBoard
import com.ivandsky.puzzlebox.gameui.replay.ReplayBoardOptions
import com.ivandsky.puzzlebox.gameui.replay.rememberStepsForward
import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strands.StrandsPuzzle
import com.ivandsky.puzzlebox.games.strands.StrandsReplayMove
import com.ivandsky.puzzlebox.games.strands.StrandsSession
import com.ivandsky.puzzlebox.games.strands.StrandsState
import com.ivandsky.puzzlebox.games.strands.engine.StrandsVerdict
import com.ivandsky.puzzlebox.games.strands.render.StrandsRendererModel
import com.ivandsky.puzzlebox.games.strandsui.chrome.WordSlotLine

/**
 * Strands' read-only replay board: the committed board and the marks live play derives from it
 * (found words, spangram, hint outline), drawn by the live board. No trace, verdict or input. A
 * frame that found one more word than [ReplayFrame.before] plays the live settle or link sweep
 * on a forward step while the replay plays ([ReplayBoardOptions.animate]); paused, scrubbing, stepping back or under
 * Reduce motion, every frame rests.
 *
 * A frame that added a non-theme word draws the path the player traced for it (the last Submit of [ReplayFrame.moves]), selected, as live
 * play does after the submit: playing, it is set aside (held, then dissolved) on a forward step while playing; at rest it stays selected,
 * so the letters picked can be read.
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
        val forward = rememberStepsForward(options, frame.atMs)
        val model = remember(session.state) { StrandsRendererModel.of(session.state) }
        val event = remember(session.state, before) { foundEventOf(before, session.state) }
        // A word that was shown at rest (paused, scrubbed) must not play when the replay resumes on the same frame.
        var rested by remember { mutableIntStateOf(-1) }
        SideEffect { if (!forward) rested = event?.seq ?: -1 }
        val badges = remember(session.state.puzzle, frame.hinted) { hintBadges(session.state.puzzle, frame.hinted) }
        val slot = remember(session.state, before) { submittedSlotOf(before, session.state) }
        val nonTheme = remember(session.state, before, frame.moves) { nonThemePathOf(before, session.state, frame.moves) }
        val setAside = remember(nonTheme) { nonTheme?.let { TraceEnd(it, TraceEndKind.Bonus, seq = session.state.found.size + session.state.nonTheme.size) } }
        Column(modifier) {
            WordSlotLine(slot, model.foundCount, model.totalCount, reduceMotion = !forward)
            StrandsBoardContent(
                model = model,
                found = event?.takeIf { forward && it.seq != rested },
                reduceMotion = !forward,
                // At rest the word stays selected; playing, it leaves as live play's set-aside word does.
                trace = { if (forward) Trace() else Trace(nonTheme.orEmpty()) },
                onTrace = {},
                traceEnd = { setAside.takeIf { forward } },
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

/**
 * The path traced for the one new non-theme word on top of [before]: the last Submit among [moves], the moves of the frame's
 * stamp group, as recorded. Null for any other step, and when [moves] holds no Submit.
 */
internal fun nonThemePathOf(before: StrandsState?, after: StrandsState, moves: List<ReplayMove>): List<Coord>? {
    if (before == null || after.found.size != before.found.size || after.nonTheme.size != before.nonTheme.size + 1) return null
    return moves.asReversed().firstNotNullOfOrNull { (StrandsReplayMove.of(it, after.puzzle) as? StrandsReplayMove.Submit)?.path }
}

/** The first letter cell of every word whose cells [hinted] names. */
private fun hintBadges(puzzle: StrandsPuzzle, hinted: Set<Int>): Set<Coord> {
    if (hinted.isEmpty()) return emptySet()
    return (puzzle.themeWords + puzzle.spangram)
        .filter { word -> word.cells.first().let { it.row * puzzle.cols + it.col } in hinted }
        .mapTo(HashSet()) { it.cells.first() }
}
