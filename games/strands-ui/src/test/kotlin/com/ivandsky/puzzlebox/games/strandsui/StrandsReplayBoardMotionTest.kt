package com.ivandsky.puzzlebox.games.strandsui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.ivandsky.puzzlebox.games.strands.StrandsReplayMove
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * F2 / F19 / R13: what the Strands replay board draws for a forward step, sampled from its rendered pixels under a paused,
 * hand-stepped clock. A found word's settle and a non-theme word's held path play only while animating; at rest the
 * non-theme word stays traced and a found word is plain. A theme or spangram step never draws a trace. A backward
 * step (an earlier `atMs`, as a seek back while playing) is a rest frame even with `animate` (D1).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-420dpi")
class StrandsReplayBoardMotionTest {

    @get:Rule
    val rule = createComposeRule()

    private val rig = StrandsReplayRig(rule)

    private val nonTheme = StrandsReplayScript.nonTheme
    private val theme = StrandsReplayScript.theme
    private val spangram = StrandsReplayScript.spangram

    // The one way ARTS spells on the board, so the drawn path is the word's.
    private val artsPath = RealDay.firstHintWords[0]

    private val nonThemeStep = StrandsReplayScript.frameAfter(listOf(nonTheme))

    /** The board with the word submitted but no step behind it: nothing traced, found or settling. */
    private fun bare(vararg moves: StrandsReplayMove) = StrandsReplayScript.bareFrameAfter(moves.toList())

    @Test
    fun `at rest, a non-theme word stays traced as the word spells on the board`() {
        rig.show(nonThemeStep, animate = false, referenceTrace = artsPath)
        val board = rig.board()
        val reference = rig.reference()
        assertTrue("the board draws the path ARTS spells", board.sameAs(reference))

        rig.update(bare(nonTheme))
        assertTrue("without the step there is no path", !rig.board().sameAs(reference))
    }

    @Test
    fun `playing, a non-theme word shows its path, then lets it go`() {
        rig.show(bare(), animate = true)
        val untraced = rig.board()

        rig.update(nonThemeStep)
        val early = rig.frames(3)
        assertTrue("the path is on the board as the step lands", early.any { !it.sameAs(untraced) })

        assertTrue("the path is gone once it has dissolved", rig.boardAfter(2_000).sameAs(untraced))
    }

    @Test
    fun `playing, a non-theme word holds its path about a second, then dissolves`() {
        rig.show(bare(), animate = true)
        val untraced = rig.board()

        rig.update(nonThemeStep)
        val held = rig.boardAfter(StrandsMotion.ReplayBonusHoldMs - 100L)
        assertTrue("the path is still selected at about 900 ms", !held.sameAs(untraced))
        assertTrue("and still whole, not dissolving", held.sameAs(rig.boardAfter(0)))

        val gone = rig.boardAfter(100L + MotionTokens.DissolveDuration + 200L)
        assertTrue("the path is gone after the hold and the dissolve", gone.sameAs(untraced))
    }

    private fun restingStepIsPlain(found: StrandsReplayMove) {
        rig.show(StrandsReplayScript.frameAfter(listOf(nonTheme, found)), animate = false, referenceTrace = emptyList())
        assertTrue("at rest, the step draws no trace", rig.board().sameAs(rig.reference()))
    }

    @Test
    fun `at rest, a theme word draws no trace`() = restingStepIsPlain(theme)

    @Test
    fun `at rest, the spangram draws no trace`() = restingStepIsPlain(spangram)

    @Test
    fun `playing, a found word settles`() {
        rig.show(bare(), animate = true)
        rig.update(StrandsReplayScript.frameAfter(listOf(theme)))
        val frames = rig.frames()
        val settled = rig.boardAfter(3_000)
        assertTrue("a frame mid-settle differs from the settled board", frames.any { !it.sameAs(settled) })
    }

    @Test
    fun `paused or scrubbing, a found word shows at once`() {
        rig.show(bare(), animate = false)
        val before = rig.board()
        rig.update(StrandsReplayScript.frameAfter(listOf(theme)))
        val frames = rig.frames()
        assertTrue("every frame is the settled board", frames.all { it.sameAs(frames.last()) })
    }

    @Test
    fun `resuming play on a rested non-theme step sets its path aside as it does when playing`() {
        rig.show(bare(nonTheme), animate = false)
        val untraced = rig.board()
        rig.update(nonThemeStep)
        val rest = rig.board()
        assertTrue("at rest the path is drawn", !rest.sameAs(untraced))

        rig.update(animate = true)
        assertTrue("the path is on the board as play resumes", rig.frames(3).any { !it.sameAs(untraced) })
        assertTrue("the path is gone once it has dissolved", rig.boardAfter(2_000).sameAs(untraced))
    }

    @Test
    fun `resuming play on a rested found word does not settle it again`() {
        rig.show(StrandsReplayScript.frameAfter(listOf(theme)), animate = false)
        val rest = rig.board()
        rig.update(animate = true)
        assertTrue("the word stays as it rests", rig.frames().all { it.sameAs(rest) })
    }

    // A seek back while playing: the frame shown is at an earlier atMs than the one before it.
    @Test
    fun `playing, a found word that appears on a backward step shows at once`() {
        rig.show(StrandsReplayScript.bareFrameAfter(emptyList(), atMs = 2_000), animate = true)
        rig.update(StrandsReplayScript.frameAfter(listOf(theme), atMs = 1_000))
        val frames = rig.frames()
        assertTrue("every frame is the settled board", frames.all { it.sameAs(frames.last()) })
        assertTrue("the board stays as it is", rig.boardAfter(3_000).sameAs(frames.last()))
    }

    @Test
    fun `playing, a non-theme word on a backward step stays traced`() {
        rig.show(StrandsReplayScript.bareFrameAfter(emptyList(), atMs = 2_000), animate = true, referenceTrace = artsPath)
        rig.update(StrandsReplayScript.frameAfter(listOf(nonTheme), atMs = 1_000))
        assertTrue("the path is drawn as at rest", rig.board().sameAs(rig.reference()))
        val later = rig.boardAfter(2_000)
        assertTrue("the path is still drawn once the live dissolve would have ended", later.sameAs(rig.reference()))
    }
}
