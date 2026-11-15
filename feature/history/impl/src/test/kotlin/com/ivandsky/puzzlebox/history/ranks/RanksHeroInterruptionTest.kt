package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertTextEquals
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.MorphAlpha
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.colors
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * failure-13: however a scrub between two boards ends, the hero card at rest shows the target board's place and score
 * and nothing of the board it left.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-land-mdpi")
class RanksHeroInterruptionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val zip = HeroFace("Zip", Standing.Place(3), "1:15 · 1 hint")
    private val overall = HeroFace("Overall", Standing.Place(12), "1,240 pts")
    private var face by mutableStateOf(zip)
    private var was by mutableStateOf<HeroFace?>(null)
    private var progress by mutableFloatStateOf(1f)
    private var generation by mutableIntStateOf(0)

    // A configuration change throws the composition away; bumping the key does the same to everything remembered.
    private fun show() = composeRule.setContent { key(generation) { Card() } }

    @androidx.compose.runtime.Composable
    private fun Card() {
        PuzzleBoxTheme(darkTheme = false) {
            val accent = AccentSlot.Blue.colors()
            RanksHeroCard(face, { accent.container }, { accent.onContainer }, was = was, progress = { progress })
        }
    }

    private fun scrubTo(target: HeroFace, at: Float) {
        was = face
        face = target
        progress = at
        composeRule.mainClock.advanceTimeBy(80L)
    }

    private fun assertAtRest(target: HeroFace) {
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag("ranks.hero.place").assertCountEquals(1)
        composeRule.onNodeWithTag("ranks.hero.place").assertContentDescriptionEquals("Your place: ${target.standing.describe()}")
        composeRule.onNodeWithTag("ranks.hero.score").assertTextEquals(target.line())
        val unfinished = composeRule.onAllNodes(
            SemanticsMatcher("a layer not fully shown") { MorphAlpha in it.config && it.config[MorphAlpha] < 1f },
            useUnmergedTree = true,
        ).fetchSemanticsNodes()
        assertEquals("every morph layer is whole", 0, unfinished.size)
    }

    @Test
    fun `board data landing mid-scrub leaves the target board's face`() {
        show()
        scrubTo(overall, at = 0.5f)

        face = overall.copy(standing = Standing.Place(7), score = "980 pts")
        Snapshot.sendApplyNotifications()
        composeRule.mainClock.advanceTimeBy(50L)
        was = null
        progress = 1f

        assertAtRest(face)
    }

    @Test
    fun `a cancelled touch settles back on the board the hero started on`() {
        show()
        scrubTo(overall, at = 0.4f)

        face = zip
        was = null
        progress = 0f

        assertAtRest(zip)
    }

    @Test
    fun `a move that interrupts a scrub shows only the board it lands on`() {
        show()
        scrubTo(overall, at = 0.5f)
        val tango = HeroFace("Tango", Standing.None, null)

        scrubTo(tango, at = 0.3f)
        was = null
        face = tango
        progress = 1f

        assertAtRest(tango)
    }

    @Test
    fun `a target slice that goes null drops the scrub and shows the board it is on`() {
        show()
        scrubTo(overall, at = 0.5f)

        was = null
        face = HeroFace("Overall", Standing.None, null)

        assertAtRest(face)
    }

    @Test
    fun `rotation mid-scrub recomposes to the target board alone`() {
        show()
        scrubTo(overall, at = 0.5f)

        was = null
        progress = 1f
        generation++

        assertAtRest(overall)
    }

    @Test
    fun `a face that changes again mid-fade, with no scrub, leaves the last face alone`() {
        face = HeroFace("Zip", Standing.Place(3), "3,917 pts")
        show()

        face = HeroFace("Zip", Standing.None, null)
        Snapshot.sendApplyNotifications()
        composeRule.mainClock.advanceTimeBy(100L)
        face = HeroFace("Zip", Standing.Pending, null)

        assertAtRest(face)
    }
}
