package com.ivandsky.puzzlebox.insights.recap

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The plaque under the sticker sheet keeps the height of its tallest label, no more, so a peel never moves the rows below. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class MomentPlaqueTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val moments = MomentsFixtures.default.earned

    private var width by mutableIntStateOf(380)
    private var peeled by mutableIntStateOf(0)

    private fun plaqueHeight(): Int {
        rule.waitForIdle()
        return rule.onNodeWithTag("recap.moments.plaque").fetchSemanticsNode().size.height
    }

    @Before
    fun show() = rule.setContent {
        PuzzleBoxTheme { MomentPlaque(moments[peeled], moments, FastestFixtures.gameName, Modifier.width(width.dp)) }
    }

    @Test
    fun `labels that all fit on one line do not reserve a second`() {
        val roomy = plaqueHeight()
        width = 150
        val cramped = plaqueHeight()

        assertTrue("one-line plaque $roomy should be shorter than the wrapped one $cramped", roomy < cramped)
    }

    @Test
    fun `peeling a short label on a plaque where another wraps keeps the height`() {
        // At 150dp "First under 1:00 in Zip" (index 4) wraps; "Full set done" (index 1) does not, but the plaque is as tall for both.
        width = 150
        peeled = 4
        val wrapped = plaqueHeight()
        peeled = 1

        assertEquals(wrapped, plaqueHeight())
    }
}
