package com.ivandsky.puzzlebox.games.strandsui.board

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strandsui.LocalTokenComposed
import com.ivandsky.puzzlebox.games.strandsui.StrandsBoard
import com.ivandsky.puzzlebox.games.strandsui.StrandsPlayController
import com.ivandsky.puzzlebox.games.strandsui.StrandsUiFixtures
import com.ivandsky.puzzlebox.gameui.Adopt
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A trace step redraws only what it changes (A3): dragging across five letters of the 4 × 4
 * fixture recomposes none of the eleven letters the trace never reaches.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h700dp")
class StrandsBoardRecompositionTest {

    @get:Rule
    val rule = createComposeRule()

    private val controller = StrandsPlayController(
        PlayEnvironment(CoroutineScope(Dispatchers.Unconfined), {}, {}),
    ).also { assertEquals(Adopt.Adopted, it.adoptToday(StrandsUiFixtures.envelope(), StrandsUiFixtures.ref)) }

    private val compositions = mutableMapOf<Coord, Int>()

    private fun TouchInjectionScope.centreOf(at: Coord) =
        Offset(width * (at.col + 0.5f) / 4, height * (at.row + 0.5f) / 4)

    @Test
    fun `a drag across five letters recomposes no letter off the trace`() {
        rule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                CompositionLocalProvider(LocalTokenComposed provides { compositions[it] = (compositions[it] ?: 0) + 1 }) {
                    Box(Modifier.size(400.dp, 300.dp)) { StrandsBoard(controller) }
                }
            }
        }
        rule.waitForIdle()
        val before = compositions.toMap()
        val path = StrandsUiFixtures.row(1) + Coord(0, 3)
        val board = rule.onNodeWithTag("strands.board")
        board.performTouchInput { down(centreOf(path.first())) }
        // One cell per frame, so every step is its own recomposition rather than one batched change.
        path.zipWithNext { from, to ->
            board.performTouchInput {
                (1..4).forEach { moveTo(centreOf(from) + (centreOf(to) - centreOf(from)) * (it / 4f)) }
            }
            rule.waitForIdle()
        }

        assertEquals(path, controller.trace.path)
        val offTrace = (0 until 4).flatMap { row -> (0 until 4).map { Coord(row, it) } } - path.toSet()
        assertEquals(
            offTrace.associateWith { 0 },
            offTrace.associateWith { (compositions[it] ?: 0) - (before[it] ?: 0) },
        )
        board.performTouchInput { up() }
    }
}
