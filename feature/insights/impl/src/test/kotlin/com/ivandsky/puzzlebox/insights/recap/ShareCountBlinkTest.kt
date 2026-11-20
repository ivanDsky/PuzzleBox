package com.ivandsky.puzzlebox.insights.recap

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.insights.share.RecapShareContent
import com.ivandsky.puzzlebox.insights.share.RecapShareImage
import com.ivandsky.puzzlebox.insights.share.RecapShareState
import com.ivandsky.puzzlebox.insights.share.ShareContract
import com.ivandsky.puzzlebox.insights.share.ShareStyle
import com.ivandsky.puzzlebox.insights.share.sharePalette
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * The Share page's arrival frame by frame (16 ms apart): while the numbers count up, the hero and every tile value are
 * drawn on every frame, and each keeps the size it was fitted to for its final text. Pixels are read from the captured
 * page; where the numbers sit is learned from the settled page.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-land-mdpi")
class ShareCountBlinkTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val ready = RecapShareState("October 2026", "Ada", restoring = false, sharing = false, failed = false)
    private val palette = sharePalette(ShareStyle.Classic, false)

    @Test
    fun `a two digit hero and its tiles are drawn on every frame of the count`() = checkCount(ShareContract.default)

    @Test
    fun `a four digit hero and its tiles are drawn on every frame of the count`() = checkCount(fourDigits)

    @Test
    fun `a hero and tiles too wide at their final value keep the size they fit to through the count`() {
        val content = RecapShareContent.of(ready.title, ready.displayName, tooWide)
        val progress = mutableStateOf(0f)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                CompositionLocalProvider(LocalGameBrands provides SolvedFixtures.brands) { RecapShareImage(content.counted(progress.value), ShareStyle.Classic) }
            }
        }
        rule.mainClock.advanceTimeBy(SettleMs)
        val sizes = COUNTED.map { p ->
            progress.value = p
            rule.mainClock.advanceTimeBy(FrameMs)
            fontSizes()
        }.toMutableList()
        // Once the count is over and everything has settled: the size the numbers were fitted to.
        rule.mainClock.advanceTimeBy(SettleMs)
        sizes += fontSizes()
        assertTrue("a number changed size during the count: $sizes", sizes.all { it == sizes.first() })
    }

    /** The font size of every text on the poster, in reading order: the labels never change, so only a value's fit can. */
    private fun fontSizes(): List<Float> = rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
        .fetchSemanticsNodes().flatMap { node ->
            val results = mutableListOf<TextLayoutResult>()
            rule.runOnUiThread { node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results) }
            results.map { it.layoutInput.style.fontSize.value }
        }

    /** Four digits still fit the hero at its full size, so the size test needs a final value that does not. */
    private val tooWide = ShareContract.default.map { if (it is RecapCard.Solved) it.copy(total = 1_206_700, hintFree = 900_000) else it }

    private val fourDigits = ShareContract.default.map { if (it is RecapCard.Solved) it.copy(total = 1067, hintFree = 900) else it }

    private fun checkCount(cards: List<RecapCard>) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                CompositionLocalProvider(LocalGameBrands provides SolvedFixtures.brands) {
                    Box(Modifier.size(852.dp, 768.dp)) { ShareCardPage(cards, ready, onShare = { _, _ -> }) }
                }
            }
        }
        val frames = (0 until FrameCount).map { capture() }
        rule.mainClock.advanceTimeBy(SettleMs - FrameCount * FrameMs)
        val settled = capture()

        val heroColor = palette.hero.toArgb()
        val hero = settled.heroRows(heroColor)
        val tiles = RecapShareContent.of(ready.title, ready.displayName, cards).tiles.map { stat ->
            settled.tileValue(palette.tile(checkNotNull(stat.card)).field.toArgb())
        }
        val counting = frames.withIndex().map { (f, frame) -> (f + 1) * FrameMs to frame }.filter { it.first in CountFrom..CountTo }
        val blank = buildList {
            counting.forEach { (time, frame) ->
                if (frame.heroHeight(hero, heroColor) == 0) add("hero@$time")
                tiles.forEachIndexed { t, tile -> if (frame.valueHeight(tile) == 0) add("tile$t@$time") }
            }
        }
        assertTrue("numbers missing on frames: $blank", blank.isEmpty())
    }

    private fun capture(): Frame {
        rule.mainClock.advanceTimeBy(FrameMs)
        val image = rule.onNodeWithTag("recap.share").captureToImage()
        return Frame(image.width, image.height, IntArray(image.width * image.height).also { image.readPixels(it, 0, 0, image.width, image.height) })
    }

    private class Frame(val width: Int, val height: Int, val pixels: IntArray) {

        /** The picked poster's middle, clear of its border and its neighbours. */
        val posterColumns get() = width * 31 / 100 until width * 69 / 100

        private fun rowsWith(columns: IntRange, inked: (Int) -> Boolean) = BooleanArray(height) { y -> columns.any { x -> inked(pixels[y * width + x]) } }

        /** The tallest run of rows holding the hero's colour in the poster's middle: the hero number, the biggest text on it. */
        fun heroRows(hero: Int): IntRange = runs(rowsWith(posterColumns) { near(it, hero, InkTolerance) }).maxBy { it.count() }

        /** Where a tile sits (its field's bounding box) and its first line of anything but field, the value above the label. */
        fun tileValue(field: Int): Tile {
            var left = width; var right = -1; var top = height; var bottom = -1
            for (y in 0 until height) for (x in posterColumns) {
                if (near(pixels[y * width + x], field, ExactTolerance)) { left = minOf(left, x); right = maxOf(right, x); top = minOf(top, y); bottom = maxOf(bottom, y) }
            }
            check(right > left && bottom > top) { "no tile in this colour" }
            // Inside the rounded corners, so only text is left on the field.
            val columns = left + Inset..right - Inset
            val text = rowsWith(columns) { !near(it, field, InkTolerance) }.also { for (y in 0 until height) if (y !in top + Inset..bottom - Inset) it[y] = false }
            return Tile(runs(text).first(), columns, field)
        }

        /** The rows from the first to the last that hold the hero number in [columns] within [rows] padded; 0 when none does. */
        fun heroHeight(rows: IntRange, hero: Int): Int = height(rows, rowsWith(posterColumns) { near(it, hero, InkTolerance) })

        /** The same for a [tile]'s value: rows holding anything but its field. */
        fun valueHeight(tile: Tile): Int = height(tile.rows, rowsWith(tile.columns) { !near(it, tile.field, InkTolerance) })

        private fun height(rows: IntRange, has: BooleanArray): Int {
            val padded = (rows.first - Pad).coerceAtLeast(0)..(rows.last + Pad).coerceAtMost(height - 1)
            val hit = padded.filter { has[it] }
            return if (hit.isEmpty()) 0 else hit.last() - hit.first() + 1
        }

        private fun runs(rows: BooleanArray): List<IntRange> {
            val out = mutableListOf<IntRange>()
            var start = -1
            var last = -1
            for (y in rows.indices) if (rows[y]) {
                if (start < 0) start = y else if (y - last > RunGap) { out += start..last; start = y }
                last = y
            }
            if (start >= 0) out += start..last
            return out
        }

        private fun near(a: Int, b: Int, tolerance: Int) =
            abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)) < tolerance &&
                abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)) < tolerance &&
                abs((a and 0xFF) - (b and 0xFF)) < tolerance
    }

    private class Tile(val rows: IntRange, val columns: IntRange, val field: Int)

    private companion object {
        const val FrameMs = 16L
        const val FrameCount = 100
        const val SettleMs = 3_000L
        val COUNTED = (0..20).map { it / 20f }
        const val CountFrom = 848L
        const val CountTo = 1_500L
        const val Pad = 4
        const val Inset = 14
        const val RunGap = 3
        const val ExactTolerance = 3
        const val InkTolerance = 40
    }
}
