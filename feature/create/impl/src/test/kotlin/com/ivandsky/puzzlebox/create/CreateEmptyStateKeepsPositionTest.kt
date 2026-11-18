package com.ivandsky.puzzlebox.create

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Ticket 1509: the first-time Custom list, on a screen too short for all its games, keeps its scroll when its games arrive again. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h260dp")
class CreateEmptyStateKeepsPositionTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private var games by mutableStateOf(PreviewGames)

    private fun scrolled(): Float =
        rule.onNodeWithTag("create.empty").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()

    @Test
    fun `the first-time list keeps its scroll when its games arrive again`() {
        rule.setContent {
            PuzzleBoxTheme { CreateEmptyState(games = games, onStart = { _, _ -> }, onImport = {}) }
        }
        rule.onNodeWithTag("create.empty").performTouchInput { swipeUp() }
        rule.waitForIdle()
        val before = scrolled()
        assertTrue("the first-time list scrolled", before > 0f)

        games = PreviewGames.map { it.copy(name = "${it.name} ") }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(1_000)

        assertEquals(before, scrolled(), 0.5f)
    }
}
