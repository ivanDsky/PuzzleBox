package com.ivandsky.puzzlebox.games.queensui

import com.ivandsky.puzzlebox.designsystem.spotlight.SpotlightRole
import com.ivandsky.puzzlebox.gameui.Adopt
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.games.queens.QueensSession
import com.ivandsky.puzzlebox.games.queens.QueensSessionStep
import com.ivandsky.puzzlebox.games.queensui.hint.QueensSpotlightMapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The first press on the Queens play board shows the first-move step. The play fixture opens on a one-cell region,
 * which a first move passes over for a row the player has to work out.
 */
class QueensFirstMoveHintTest {

    private val ui = QueensPlayFixtures.playUi()

    private val fresh = QueensSession(ref = null, puzzle = QueensPlayFixtures.puzzle())
    private val solution = QueensPlayFixtures.solution.toSet()

    @Test
    fun `the first press shows the first-move step`() {
        val expected: Map<Int, SpotlightRole> =
            QueensSpotlightMapper.map(QueensSessionStep.next(fresh, solution), fresh.puzzle, playId = 0).roles
        val controller = ui.createController(PlayEnvironment(CoroutineScope(Dispatchers.Unconfined), {}, {})) as QueensPlayController
        assertEquals(Adopt.Adopted, controller.adoptToday(QueensPlayFixtures.envelope(), QueensPlayFixtures.ref()))
        controller.requestHint()

        assertEquals(expected, checkNotNull(checkNotNull(controller.board).spotlight).roles)
    }
}
