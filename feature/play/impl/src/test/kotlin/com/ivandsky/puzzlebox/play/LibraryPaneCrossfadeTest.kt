package com.ivandsky.puzzlebox.play

import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.model.GameId
import org.junit.After
import org.junit.Assert.assertEquals
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
 * The two-pane Games right pane (TabletCrossfade, backlog #1118): picking another row fades the old page out and the new
 * one in, in place, while the list pane stays put; the shown row does nothing; with the flag off it is one frame.
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

    private fun show(crossfade: Boolean) {
        composeRule.setContent {
            var selected by remember { mutableStateOf(GameId("zip")) }
            PuzzleBoxTheme(darkTheme = false) {
                LibraryScreen(
                    state = previewState().copy(selected = selected),
                    page = null,
                    month = null,
                    layout = PlayLayout.TwoPane,
                    hasLesson = { false },
                    onSelect = { selected = it },
                    onPlay = { _, _ -> },
                    onRetry = {},
                    onOpenLesson = {},
                    calendar = CalendarActions(),
                    onOpenRanks = { _, _ -> },
                    paneCrossfade = crossfade,
                )
            }
        }
        composeRule.mainClock.autoAdvance = false
        composeRule.mainClock.advanceTimeBy(SETTLE_MILLIS)
    }

    private fun pagesShown() = listOf("zip", "tango").map { composeRule.onNodeWithTag("library.page.$it").isShown() }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.isShown(): Boolean =
        runCatching { assertExists() }.isSuccess

    private fun pick(game: String) {
        composeRule.onNodeWithTag("library.row.$game").performClick()
    }

    @Test
    fun `picking another row composes both pages mid-fade and only the new one once settled`() {
        show(crossfade = true)
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
        show(crossfade = true)
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
    fun `picking the row already selected does nothing`() {
        show(crossfade = true)

        pick("zip")
        composeRule.mainClock.advanceTimeBy(100L)

        assertEquals(listOf(true, false), pagesShown())
    }

    @Test
    fun `reduced motion fades over the short reduced fade`() {
        setAnimatorScale(0f)
        show(crossfade = true)

        pick("tango")
        composeRule.mainClock.advanceTimeBy(60L)
        assertEquals("both pages mid-fade", listOf(true, true), pagesShown())

        composeRule.mainClock.advanceTimeBy(200L)
        assertEquals(listOf(false, true), pagesShown())
    }

    @Test
    fun `with the flag off the new page replaces the old in one frame`() {
        show(crossfade = false)

        pick("tango")
        composeRule.mainClock.advanceTimeBy(16L)

        assertEquals(listOf(false, true), pagesShown())
    }

    private companion object {
        const val SETTLE_MILLIS = 2_000L
    }
}
