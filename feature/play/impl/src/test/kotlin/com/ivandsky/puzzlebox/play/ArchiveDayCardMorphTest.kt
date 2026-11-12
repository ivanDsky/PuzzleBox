package com.ivandsky.puzzlebox.play

import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.ArchiveDay
import com.ivandsky.puzzlebox.domain.ArchiveProgress
import com.ivandsky.puzzlebox.domain.GameStats
import com.ivandsky.puzzlebox.domain.PastDayStatus
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.GameCardModel
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Switching the day on the card morphs only the text that differs: the number and the eyebrow blur across in place
 * while "Zip #", "Solved" and the "See result" button are drawn once, at full opacity, throughout. Nothing clips.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h800dp")
class ArchiveDayCardMorphTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val today = LocalDate.parse("2026-11-05")
    private var day by mutableStateOf(solvedDay(603))

    private fun solvedDay(number: Int) =
        ArchiveDay(today.minusDays((610 - number).toLong()), number, PastDayStatus.Solved(95_000L), archived = false, openedOn = null)

    private fun setAnimatorScale(scale: Float) {
        val resolver = RuntimeEnvironment.getApplication().contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
    }

    @After
    fun resetScale() = setAnimatorScale(1f)

    /** Shows day 603, solved; the clock is paused from here on. */
    private fun cardOnDay603() {
        val row = LibraryRowModel(
            card = GameCardModel(
                game = GameId("zip"),
                name = "Zip",
                brand = GameBrand(AccentSlot.Blue, GameGlyphs.Zip),
                status = TodayStatus.NotStarted,
                rankOrder = RankOrder.TimeThenHints,
                puzzleNumber = 10,
                best = null,
            ),
            today = 10,
            progress = ArchiveProgress(today.minusDays(9)..today, 10, 0, 0),
            stats = GameStats(0, 0, 0, 0, null, null, null, RankOrder.TimeThenHints),
            rules = emptyList(),
        )
        composeRule.setContent {
            PuzzleBoxTheme {
                ArchiveDayCard(row, today, day, offline = false, onPlay = {}, onRetry = {}, onBackToToday = {})
            }
        }
        composeRule.mainClock.autoAdvance = false
        composeRule.mainClock.advanceTimeBy(1_000L)
    }

    private fun switchTo(number: Int) {
        day = solvedDay(number)
        // The clock is stepped by hand, so flush the write before the next frame.
        Snapshot.sendApplyNotifications()
    }

    private fun texts(text: String) = composeRule.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes()

    private fun drawn(tag: String) = composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size

    /** How much of the node shows: its morph layer's opacity, or 1 for text no morph cell touches. */
    private fun opacity(node: SemanticsNode): Float {
        var at: SemanticsNode? = node
        while (at != null) {
            // Read by name: the key is the card's own, and a test of what is drawn should not need its symbol.
            at.config.firstOrNull { it.key.name == "MorphAlpha" }?.let { return it.value as Float }
            at = at.parent
        }
        return 1f
    }

    @Test
    fun `midway through the morph the unchanged text is drawn once at full opacity and both numbers are part faded`() {
        cardOnDay603()
        switchTo(604)
        composeRule.mainClock.advanceTimeBy(100L)
        for (still in listOf("Zip #", "Solved", "See result")) {
            val nodes = texts(still)
            assertEquals("$still drawn", 1, nodes.size)
            assertEquals("$still opacity", 1f, opacity(nodes.single()), 0f)
        }
        val old = texts("603").single()
        val new = texts("604").single()
        assertTrue("old number part faded: ${opacity(old)}", opacity(old) > 0f && opacity(old) < 1f)
        assertTrue("new number part faded: ${opacity(new)}", opacity(new) > 0f && opacity(new) < 1f)
    }

    @Test
    fun `no line of the card clips what it draws`() {
        cardOnDay603()
        switchTo(604)
        composeRule.mainClock.advanceTimeBy(100L)
        for (text in listOf("603", "604", "Zip #", "Solved", "See result")) {
            var at: SemanticsNode? = texts(text).single()
            while (at != null && at.config.getOrNull(SemanticsProperties.TestTag) != "library.day_card") {
                // The button and the tag pill round their own surface; that clip is theirs, not a line's.
                val clips = at.layoutInfo.getModifierInfo().map { it.modifier.toString() }
                    .filter { "clip=true" in it && "CornerSize(size = 50.0%)" !in it }
                assertTrue("$text is clipped by $clips", clips.isEmpty())
                at = at.parent
            }
        }
    }

    @Test
    fun `a day switch settles on the new day only`() {
        cardOnDay603()
        switchTo(604)
        composeRule.mainClock.advanceTimeBy(600L)
        assertEquals(0, texts("603").size)
        assertEquals(1, texts("604").size)
        assertEquals(1f, opacity(texts("604").single()), 0f)
        assertEquals(0, drawn("library.day_card.day.${solvedDay(603).date}"))
        assertEquals(1, drawn("library.day_card.day.${solvedDay(604).date}"))
        assertEquals(1, drawn("library.play"))
        assertEquals(1, drawn("library.day_card.back"))
    }

    @Test
    fun `a rapid switch from 603 to 604 to 602 ends on 602 only`() {
        cardOnDay603()
        switchTo(604)
        composeRule.mainClock.advanceTimeBy(100L)
        switchTo(602)
        composeRule.mainClock.advanceTimeBy(100L)
        assertTrue("604 is leaving, from where it was", texts("604").all { opacity(it) < 1f })
        composeRule.mainClock.advanceTimeBy(1_000L)
        assertEquals(0, texts("603").size)
        assertEquals(0, texts("604").size)
        assertEquals(1, texts("602").size)
        assertEquals(1f, opacity(texts("602").single()), 0f)
    }

    @Test
    fun `reduced motion swaps the day at once`() {
        setAnimatorScale(0f)
        cardOnDay603()
        switchTo(604)
        repeat(3) { frame ->
            composeRule.mainClock.advanceTimeByFrame()
            assertEquals("old number at frame $frame", 0, texts("603").size)
            assertEquals("new number at frame $frame", 1, texts("604").size)
            assertFalse("new number faded at frame $frame", opacity(texts("604").single()) < 1f)
        }
    }
}
