package com.ivandsky.puzzlebox.play

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.model.GameId
import java.time.YearMonth
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.KoinApplication
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The two-pane Games right pane (backlog #1118): picking another row fades the old page out and the new
 * one in, in place, while the list pane stays put; the shown row does nothing.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp")
class LibraryPaneCrossfadeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var koin: KoinApplication? = null

    @Before
    fun startGraph() {
        koin = startGamePageRankKoin(previewState().rows.map { it.card.game })
    }

    @After
    fun stopGraph() {
        setAnimatorScale(1f)
        koin?.close()
    }

    private fun setAnimatorScale(scale: Float) {
        Settings.Global.putFloat(RuntimeEnvironment.getApplication().contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
    }

    private val pageGame = mutableStateOf(GameId("zip"))
    private var followPick = true

    private fun pageFor(game: GameId) = ArchivePageState(game, YearMonth.of(2026, 11), emptyList(), null)

    private fun show(settle: Boolean = true) {
        // Before setContent: otherwise its idle wait runs every entrance animation out first.
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            var selected by remember { mutableStateOf(GameId("zip")) }
            PuzzleBoxTheme(darkTheme = false) {
                LibraryScreen(
                    state = previewState().copy(selected = selected),
                    page = pageFor(pageGame.value),
                    month = null,
                    layout = PlayLayout.TwoPane,
                    hasLesson = { false },
                    onSelect = {
                        selected = it
                        if (followPick) pageGame.value = it
                    },
                    onPlay = { _, _ -> },
                    onRetry = {},
                    onOpenLesson = {},
                    calendar = CalendarActions(),
                    onOpenRanks = { _, _ -> },
                    onMakeOne = {},
                )
            }
        }
        if (settle) composeRule.mainClock.advanceTimeBy(SETTLE_MILLIS)
    }

    private fun pagesShown() = listOf("zip", "tango").map { composeRule.onNodeWithTag("library.page.$it").isShown() }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.isShown(): Boolean =
        runCatching { assertExists() }.isSuccess

    private fun pick(game: String) {
        composeRule.onNodeWithTag("library.row.$game").performClick()
    }

    @Test
    fun `picking another row composes both pages mid-fade and only the new one once settled`() {
        show()
        assertEquals(listOf(true, false), pagesShown())
        val listLeft = composeRule.onNodeWithTag("library.row.zip").getBoundsInRoot().left

        pick("tango")
        composeRule.mainClock.advanceTimeBy(100L)

        assertEquals("both pages mid-fade", listOf(true, true), pagesShown())
        assertEquals("the list pane is still", listLeft, composeRule.onNodeWithTag("library.row.zip").getBoundsInRoot().left)
        composeRule.mainClock.advanceTimeBy(SETTLE_MILLIS)
        assertEquals(listOf(false, true), pagesShown())
    }

    @Test
    fun `both pages hold one position while fading`() {
        show()
        val before = composeRule.onNodeWithTag("library.page.zip").getBoundsInRoot()

        pick("tango")
        composeRule.mainClock.advanceTimeBy(100L)

        val incoming = composeRule.onNodeWithTag("library.page.tango").getBoundsInRoot()
        val outgoing = composeRule.onNodeWithTag("library.page.zip").getBoundsInRoot()
        assertEquals(before.left, incoming.left)
        assertEquals(before.top, incoming.top)
        assertEquals(before.left, outgoing.left)
        assertEquals(before.top, outgoing.top)
        assertEquals(before.right - before.left, incoming.right - incoming.left)
    }

    @Test
    @Config(sdk = [34], qualifiers = "w1280dp-h360dp")
    fun `a scrolled list keeps its offset while the pane fades`() {
        show()
        composeRule.mainClock.autoAdvance = true
        val last = composeRule.onNodeWithTag("library.row.minisudoku").performScrollTo()
        composeRule.mainClock.autoAdvance = false
        val scrolledTop = last.getBoundsInRoot().top

        pick("minisudoku")
        composeRule.mainClock.advanceTimeBy(100L)

        assertEquals(scrolledTop, composeRule.onNodeWithTag("library.row.minisudoku").getBoundsInRoot().top)
        composeRule.mainClock.advanceTimeBy(SETTLE_MILLIS)
        assertEquals(scrolledTop, composeRule.onNodeWithTag("library.row.minisudoku").getBoundsInRoot().top)
    }

    @Test
    fun `a picked game's page waits for its calendar, so nothing fills in under the fade`() {
        followPick = false
        show()

        pick("tango")
        composeRule.mainClock.advanceTimeBy(100L)
        assertEquals("the new page is not shown empty", listOf(true, false), pagesShown())

        composeRule.runOnUiThread {
            pageGame.value = GameId("tango")
            Snapshot.sendApplyNotifications()
        }
        composeRule.mainClock.advanceTimeBy(100L)
        assertEquals("the page fades in with its data", listOf(true, true), pagesShown())
        composeRule.mainClock.advanceTimeBy(SETTLE_MILLIS)
        assertEquals(listOf(false, true), pagesShown())
    }

    @Test
    fun `a calendar that never arrives does not hold the pane forever`() {
        followPick = false
        show()

        pick("tango")
        composeRule.mainClock.advanceTimeBy(SETTLE_MILLIS)

        assertEquals(listOf(false, true), pagesShown())
    }

    @Test
    fun `games rows are at full alpha in their final place on the first frame`() {
        show(settle = false)
        composeRule.mainClock.advanceTimeBy(16L)
        val tag = "library.row.tango"
        val first = composeRule.onNodeWithTag(tag).captureToImage().toPixelMap().buffer
        val firstTop = composeRule.onNodeWithTag(tag).getBoundsInRoot().top
        composeRule.mainClock.advanceTimeBy(SETTLE_MILLIS)
        val settled = composeRule.onNodeWithTag(tag).captureToImage().toPixelMap().buffer
        assertTrue(first.contentEquals(settled) && firstTop == composeRule.onNodeWithTag(tag).getBoundsInRoot().top)
    }

    @Test
    fun `picking the row already selected does nothing`() {
        show()

        pick("zip")
        composeRule.mainClock.advanceTimeBy(100L)

        assertEquals(listOf(true, false), pagesShown())
    }

    @Test
    fun `reduced motion fades over the short reduced fade`() {
        setAnimatorScale(0f)
        show()

        pick("tango")
        composeRule.mainClock.advanceTimeBy(60L)
        assertEquals("both pages mid-fade", listOf(true, true), pagesShown())

        composeRule.mainClock.advanceTimeBy(200L)
        assertEquals(listOf(false, true), pagesShown())
    }

    @Test
    fun `the old page holds opaque under the new one fading in, never a dip to the background`() {
        val rows = previewState().rows
        composeRule.setContent {
            var selected by remember { mutableStateOf(rows[0]) }
            PuzzleBoxTheme(darkTheme = false) {
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    LibraryPane(LibraryPaneFrame(selected, pageFor(selected.card.game), null)) { shown ->
                        val color = if (shown.row == rows[0]) Color.Red else Color.Blue
                        Box(Modifier.fillMaxSize().background(color).clickable { selected = rows[1] }.testTag("probe.pane"))
                    }
                }
            }
        }
        composeRule.mainClock.autoAdvance = false
        composeRule.mainClock.advanceTimeBy(SETTLE_MILLIS)

        composeRule.onNodeWithTag("probe.pane").performClick()
        composeRule.mainClock.advanceTimeBy(100L)
        val map = composeRule.onRoot().captureToImage().toPixelMap()
        val pixel = map[map.width / 2, map.height / 2]

        assertTrue("outgoing page still shows: $pixel", pixel.red > 0.05f)
        assertTrue("incoming page is fading in: $pixel", pixel.blue > 0.05f)
        assertTrue("no background shows through: $pixel", pixel.green < 0.05f)
    }

    private companion object {
        const val SETTLE_MILLIS = 2_000L
    }
}
