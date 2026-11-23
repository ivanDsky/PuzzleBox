package com.ivandsky.puzzlebox.insights.hub

import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.insights.GOLDEN_TABLET
import com.ivandsky.puzzlebox.insights.domain.recap.RecapPeriod
import com.ivandsky.puzzlebox.insights.domain.recap.RecapSummary
import com.ivandsky.puzzlebox.insights.domain.recap.SummarizedPeriod
import com.ivandsky.puzzlebox.model.GameId
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The hub plays no entry motion: every tile, the first screenful, a tile scrolled into view later, a watched one or a new one
 * alike, is painted at once. Never a few animated tiles next to static ones (#2092).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_TABLET)
class HubEntryTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val months = List(24) { RecapPeriod.Month(YearMonth.of(2026, 11).minusMonths(it.toLong())) }

    private fun show() {
        // The clock never advances: a tile that animates in is still faded out at this first frame.
        rule.mainClock.autoAdvance = false
        val periods = months.mapIndexed { i, month -> SummarizedPeriod(month, RecapSummary(100 + i, listOf(GameId("alpha") to 100 + i), emptyList())) }
        // Every third month is watched: a watched tile and a new one behave the same.
        val opened = months.filterIndexed { i, _ -> i % 3 == 0 }.map { it.id }.toSet()
        rule.setContent {
            PuzzleBoxTheme {
                InsightsHubScreen(InsightsHubUiState.Ready(periods, opened = opened), onOpen = {}, onBack = {}, onOpenStats = {}, onRetry = {})
            }
        }
    }

    private fun tileTag(i: Int) = "insights.hub.month.${months[i].id}"

    /** The tiles in the grid's layout right now that show whole (the grid clips the row at its edge), as their test tags. */
    private fun shownTiles(): List<String> {
        val bounds = months.indices.map(::tileTag).mapNotNull { tag ->
            rule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().firstOrNull()?.let { tag to it.boundsInRoot.height }
        }
        val whole = bounds.maxOf { it.second }
        return bounds.filter { it.second == whole }.map { it.first }
    }

    /** A tile that is faded out paints nothing: its pixels, inside the selection outline, are all the page's. */
    private fun blank(tag: String): Boolean {
        val image: ImageBitmap = rule.onNodeWithTag(tag).captureToImage()
        val pixels = image.toPixelMap()
        val inset = image.width / 10
        val page = pixels[inset, inset]
        return (inset until image.height - inset step 4).all { y -> (inset until image.width - inset step 4).all { x -> pixels[x, y] == page } }
    }

    @Test
    fun `every tile of the opening screen is painted at the first frame, however many fit`() {
        show()

        val shown = shownTiles()
        assertTrue("a tablet shows more than five tiles at open, got ${shown.size}", shown.size > 5)
        assertEquals(emptyList<String>(), shown.filter(::blank))
    }

    @Test
    fun `a tile scrolled into view later is painted at its first frame`() {
        show()

        rule.onNodeWithTag("insights.hub.list").performScrollToIndex(months.size)

        assertFalse(blank(tileTag(months.lastIndex)))
    }

    @Test
    fun `with reduced motion every tile is painted at the first frame too`() {
        Settings.Global.putFloat(rule.activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        show()

        assertEquals(emptyList<String>(), shownTiles().filter(::blank))
    }
}
