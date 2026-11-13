package com.ivandsky.puzzlebox.games.strandsui

import com.ivandsky.puzzlebox.game.replay.ReplayCodec
import com.ivandsky.puzzlebox.game.replay.ReplayOutcome
import com.ivandsky.puzzlebox.game.replay.ReplayRecord
import com.ivandsky.puzzlebox.game.replay.StampedMove
import com.ivandsky.puzzlebox.game.replay.Timeline
import com.ivandsky.puzzlebox.games.strands.ActiveHint
import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strands.HintStage
import com.ivandsky.puzzlebox.games.strands.StrandsDefinition
import com.ivandsky.puzzlebox.games.strands.StrandsReplay
import com.ivandsky.puzzlebox.games.strands.StrandsReplayMove
import com.ivandsky.puzzlebox.games.strands.StrandsSession
import com.ivandsky.puzzlebox.games.strands.StrandsState
import com.ivandsky.puzzlebox.model.SolveReplay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The path a replay frame draws selected: only a step that added one non-theme word has one, and it is the path the player traced. */
class StrandsReplayNonThemePathTest {

    private val puzzle = StrandsUiFixtures.puzzle
    private val start = StrandsState(puzzle)
    private val initial = StrandsSession(ref = StrandsUiFixtures.ref, state = start)

    /**
     * RAKE reads several ways on this board. The first in row-major order ends on the E of PIES, above the K ([rakeFirst]);
     * [rakeSecond] goes through the A beside the R and ends on the E of BAKE.
     */
    private val rakeFirst = listOf(Coord(2, 2), Coord(1, 1), Coord(1, 2), Coord(0, 2))
    private val rakeSecond = listOf(Coord(2, 2), Coord(2, 1), Coord(1, 2), Coord(1, 3))

    private fun submit(path: List<Coord>) = StrandsReplayMove.Submit(path).toReplayMove(puzzle.cols)

    private fun timelineOf(vararg moves: StampedMove): Timeline {
        val stored = checkNotNull(ReplayCodec.encode(ReplayRecord(StrandsDefinition.id, ReplayOutcome.GaveUp, 9_000, moves.toList())))
        return checkNotNull(Timeline.open(StrandsDefinition.id, StrandsReplay, initial, checkNotNull(SolveReplay.of(stored))))
    }

    @Test
    fun `a non-theme word draws the path the player traced, not the first path that spells it`() {
        val timeline = timelineOf(StampedMove(1_000, submit(rakeSecond)))
        val frame = timeline.at(1_000)

        assertEquals("the frame carries the Submit", listOf(submit(rakeSecond)), frame.moves)
        val before = (frame.before as StrandsSession).state
        val after = (frame.session as StrandsSession).state
        assertEquals(rakeSecond, nonThemePathOf(before, after, frame.moves))
    }

    @Test
    fun `the traced path is the one a later word in the same frame does not replace`() {
        // A hint at the same stamp as the Submit shares its group; the Submit is still the path to draw.
        val hint = StrandsReplayMove.Hint(0, HintStage.Outlined).toReplayMove(puzzle.cols)
        val timeline = timelineOf(StampedMove(1_000, submit(rakeSecond)), StampedMove(1_000, hint))
        val frame = timeline.at(1_000)

        val before = (frame.before as StrandsSession).state
        val after = (frame.session as StrandsSession).state
        assertEquals(listOf(submit(rakeSecond), hint), frame.moves)
        assertEquals(rakeSecond, nonThemePathOf(before, after, frame.moves))
    }

    @Test
    fun `a Submit that traced the first spelling still draws it`() {
        val frame = timelineOf(StampedMove(1_000, submit(rakeFirst))).at(1_000)
        assertEquals(
            rakeFirst,
            nonThemePathOf((frame.before as StrandsSession).state, (frame.session as StrandsSession).state, frame.moves),
        )
    }

    @Test
    fun `a non-theme word with no Submit among the moves draws no path`() {
        val after = start.copy(nonTheme = setOf("RAKE"))
        assertNull(nonThemePathOf(start, after, emptyList()))
        assertNull(nonThemePathOf(start, after, listOf(StrandsReplayMove.Hint(0, HintStage.Outlined).toReplayMove(puzzle.cols))))
    }

    @Test
    fun `the first frame, a found word and a hint draw no path`() {
        val submit = listOf(submit(rakeSecond))
        assertNull(nonThemePathOf(null, start.copy(nonTheme = setOf("PIKE")), submit))
        assertNull(nonThemePathOf(start, start.copy(found = listOf("PIES")), submit))
        assertNull(nonThemePathOf(start, start.copy(activeHint = ActiveHint("PIES", HintStage.Outlined)), submit))
    }
}
