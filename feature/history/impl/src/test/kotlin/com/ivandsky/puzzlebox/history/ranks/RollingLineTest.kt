package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A score line reads as its text whatever it is drawing: at rest, rolling to a new score, and scrubbed between two (user-check F16). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class RollingLineTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var text by mutableStateOf("1:05 · 1 hint")
    private var was by mutableStateOf<String?>(null)
    private var progress by mutableFloatStateOf(0f)

    private fun show() = composeRule.setContent {
        PuzzleBoxTheme(darkTheme = false) {
            val ink = MaterialTheme.colorScheme.onSurface
            RollingLine(text, MaterialTheme.typography.bodyLarge, ColorProducer { ink }, Modifier.testTag("line"), was) { progress }
        }
    }

    private fun assertReads(expected: String) {
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag("line").assertCountEquals(1)
        composeRule.onNodeWithTag("line").assertTextEquals(expected)
    }

    @Test
    fun `a line at rest reads as its text, and as the new one once it changes`() {
        show()
        assertReads("1:05 · 1 hint")

        text = "12:40 · 3 hints"
        assertReads("12:40 · 3 hints")

        text = "1,240 pts"
        assertReads("1,240 pts")
    }

    @Test
    fun `a line scrubbed between two reads as the one it goes to, there and back`() {
        was = "0:58 · 2 hints"
        show()

        for (at in listOf(0f, 0.5f, 1f, 0.2f)) {
            progress = at
            assertReads("1:05 · 1 hint")
        }
        was = null
        assertReads("1:05 · 1 hint")
    }

    @Test
    fun `a line scrubbed to one of another shape frosts over whole and still reads as its text`() {
        was = "1,240 pts"
        progress = 0.5f
        show()
        assertReads("1:05 · 1 hint")

        was = null
        assertReads("1:05 · 1 hint")
    }
}
