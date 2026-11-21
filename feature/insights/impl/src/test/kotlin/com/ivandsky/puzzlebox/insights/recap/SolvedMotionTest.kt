package com.ivandsky.puzzlebox.insights.recap

import android.content.Context
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.insights.GOLDEN_PHONE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Solved card's arrival at fixed clock points, with motion on: the tide rises from 200ms for 1700ms and the total
 * counts the solves under it; each band's name enters and its count lands as the level clears the band, the bottom band
 * first; the hint-free ribbon comes in at 2130ms, after the top band, whatever the game count.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class SolvedMotionTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private var start = 0L
    private val replays = mutableIntStateOf(0)

    private fun show(animatorScale: Float) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, animatorScale)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            key(replays.intValue) {
                PuzzleBoxTheme {
                    CompositionLocalProvider(LocalGameBrands provides SolvedFixtures.brands) {
                        SolvedCardPage(SolvedFixtures.default, "October 2026", SolvedFixtures.gameName)
                    }
                }
            }
        }
        start = rule.mainClock.currentTime
    }

    /** Moves the clock to [ms] after the card was shown (rounded up to a frame). */
    private fun at(ms: Int) {
        val elapsed = rule.mainClock.currentTime - start
        if (ms > elapsed) rule.mainClock.advanceTimeBy(ms - elapsed)
    }

    private fun node(tag: String) = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
    private fun text(tag: String) = node(tag).config[SemanticsProperties.Text].joinToString("") { it.text }
    private fun nameTop(game: String) = node("recap.solved.game.$game.name").boundsInRoot.top
    private fun counts() = games.map { text("recap.solved.game.$it.count").toInt() }
    private fun hero() = text("recap.solved.total").toInt()
    private fun ribbonCount() = text("recap.hintfree.count").substringBefore(' ').toInt()
    private fun ribbonOffscreen() = node("recap.hintfree.count").boundsInRoot.right <= 0f

    /** When the tide clears [game]'s band, from where the band stands on the sheet. */
    private fun landMs(game: String): Int {
        val sheet = node("recap.solved.games").boundsInRoot
        val band = node("recap.solved.game.$game").boundsInRoot
        return tideLandMs((sheet.bottom - band.top) / sheet.height)
    }

    @Test
    fun `the tide fills from the bottom band up and the stripe waits for the top band`() {
        show(animatorScale = 1f)
        at(0)
        // Every count is its final value from the start: it lands, it never rolls.
        assertEquals(Final, counts())
        assertEquals("the total starts from nothing", 0, hero())
        assertTrue(ribbonOffscreen())

        // The total only climbs as the tide rises, and is the whole total once it is in.
        val heroes = (0..19).map { step -> at(step * 100); hero() }
        assertTrue("the total never drops: $heroes", heroes.zipWithNext().all { (a, b) -> b >= a })
        assertTrue("the total climbs mid-tide: $heroes", heroes[10] in 1 until 96)
        at(TideInMs + Frame)
        assertEquals(96, hero())

        // 2000ms: the tide is in and the top band has landed, but the ribbon (2130) is still off the page.
        at(2000)
        assertTrue(ribbonOffscreen())
        // 2500ms: the ribbon is in and its own count is mid-roll.
        at(2500)
        assertTrue(!ribbonOffscreen())
        assertTrue("the ribbon's count is mid-roll", ribbonCount() in 1 until 71)

        at(5000)
        val home = games.associateWith(::nameTop)
        val lands = games.associateWith(::landMs)
        assertEquals(Final, counts())
        assertEquals(71, ribbonCount())
        assertTrue("the settled ribbon is on the page", !ribbonOffscreen())
        assertTrue("the bottom band lands first, the top last: $lands", games.reversed().map { lands.getValue(it) }.zipWithNext().all { (a, b) -> b > a })
        assertTrue("the top band lands inside the rise: $lands", lands.getValue("queens") in 1877..1900)
        assertTrue("the bottom band lands early: $lands", lands.getValue("strands") < 700)

        // Shown afresh: each band's name waits a rise below its place until the tide clears the band, then is home
        // after its Enter. The checks run in clock order, as the bands' windows overlap.
        replay()
        val rise = MotionTokens.EnterRise.value * rule.density.density
        val checks = games.flatMap { game -> listOf(Triple(lands.getValue(game) - 40, game, rise), Triple(lands.getValue(game) + 280, game, 0f)) }
        for ((ms, game, below) in checks.sortedBy { it.first }) {
            at(ms)
            assertEquals("$game at ${ms}ms", home.getValue(game) + below, nameTop(game), 0.5f)
        }
    }

    @Test
    fun `reduced motion shows the final counts, the full total and every band in place at once`() {
        show(animatorScale = 0f)
        at(0)

        assertEquals(Final, counts())
        assertEquals(96, hero())
        assertEquals(71, ribbonCount())
        assertTrue(!ribbonOffscreen())
    }

    /** Shows the card afresh (its entrance plays again), the clock read from the new start. */
    private fun replay() {
        Snapshot.withMutableSnapshot { replays.intValue++ }
        rule.mainClock.advanceTimeByFrame()
        start = rule.mainClock.currentTime
        assertEquals("the tide starts again", 0, hero())
    }

    private val games = listOf("queens", "tango", "zip", "sudoku", "strands")
}

private val Final = listOf(28, 24, 22, 14, 8)

/** The tide is in 200 + 1700ms after the card is shown. */
private const val TideInMs = 1900

/** The tide starts on the frame after the card is shown. */
private const val Frame = 16
