package com.ivandsky.puzzlebox.play

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.ivandsky.puzzlebox.designsystem.TabSwipeOption
import com.ivandsky.puzzlebox.designsystem.TabSwipeProto
import com.ivandsky.puzzlebox.gameui.PlayLayout
import java.io.File
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.KoinApplication
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Prototype 141 (throwaway, record-only): the frames of one tab change under one [TabSwipeOption], for the strips in
 * `.scratch/nav-rethink/prototypes/141-tab-swipe/`. A strip is `<option>_<scenario>_<light|dark>_<motion|reduced>`;
 * its frames go to `build/tab-swipe-strips/<device>/<strip>/NN.png` beside a `frames.tsv` of, per frame, the clock and
 * where the indicator and the two pages are (root pixels). `stitch.py` in the scratch folder lays them out.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class TabSwipeStripScreenshotsPhone(strip: String) : TabSwipeStripScreenshots(strip, PlayLayout.SinglePane, "phone") {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun strips(): List<Array<Any>> = tabSwipeStrips()
    }
}

@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_TABLET)
class TabSwipeStripScreenshotsTablet(strip: String) : TabSwipeStripScreenshots(strip, PlayLayout.TwoPane, "tablet") {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun strips(): List<Array<Any>> = tabSwipeStrips()
    }
}

/** A phone on its side is too short for two panes ([com.ivandsky.puzzlebox.gameui.PanePolicy]): today's behaviour only. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w891dp-h411dp-land-420dpi")
class TabSwipeStripScreenshotsPhoneLandscape(strip: String) : TabSwipeStripScreenshots(strip, PlayLayout.SinglePane, "phone-landscape") {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun strips(): List<Array<Any>> = tabSwipeTodayStrips()
    }
}

/** An upright tablet is one pane too: today's behaviour only. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w800dp-h1280dp-port-mdpi")
class TabSwipeStripScreenshotsTabletPortrait(strip: String) : TabSwipeStripScreenshots(strip, PlayLayout.SinglePane, "tablet-portrait") {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun strips(): List<Array<Any>> = tabSwipeTodayStrips()
    }
}

abstract class TabSwipeStripScreenshots(private val strip: String, private val layout: PlayLayout, private val device: String) {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val option = TabSwipeOption.valueOf(strip.split('_')[0])
    private val scenario = strip.split('_')[1]
    private val dark = strip.split('_')[2] == "dark"
    private val reduced = strip.split('_')[3] == "reduced"

    private var koin: KoinApplication? = null
    private val frames = mutableListOf<String>()
    private var from = ""
    private var to = ""
    private var zero = 0L

    /** The tablet's first game page carries the game's board, which a view model builds. */
    @Before
    fun startGraph() {
        koin = startGamePageRankKoin(previewState().rows.map { it.card.game })
    }

    @After
    fun stopGraph() {
        koin?.close()
        TabSwipeProto.option = TabSwipeOption.Today
        TabSwipeProto.switcher = false
    }

    @Test
    fun record() {
        assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        TabSwipeProto.option = option
        TabSwipeProto.switcher = scenario == "switcher"
        val games = scenario.startsWith("games") || scenario == "switcher"
        rule.showGolden(dark, animated = !reduced) { if (games) TabSwipeGames(layout) else TabSwipeCommunity(layout) }
        rule.mainClock.advanceTimeBy(SETTLE_MS)
        when (scenario) {
            "games-swipe" -> swipe("library.row.zip", TAB_SWIPE_DUELS, toward = 1)
            "games-back" -> {
                rule.onNodeWithTag("library.tab.duels").performClick()
                rule.mainClock.advanceTimeBy(SETTLE_MS)
                swipe(TAB_SWIPE_DUELS, "library.row.zip", toward = -1)
            }
            "games-tap" -> tap("library.tab.duels", "library.row.zip", TAB_SWIPE_DUELS)
            "community-swipe" -> swipe("community.page.new", "community.page.friends", toward = 1)
            "community-far" -> tap("community.tab.yours", "community.page.new", "community.page.yours")
            "switcher" -> frame("rest")
            else -> error("no scenario $scenario")
        }
        val pager = rule.onNodeWithTag("underline.pager").fetchSemanticsNode()
        directory().resolve("frames.tsv").writeText(
            "# pager x=${pager.positionInRoot.x} width=${pager.size.width}\nframe\tlabel\tms\tbar\tfrom\tto\n" + frames.joinToString("\n") + "\n",
        )
    }

    /** A drag of [DRAG_SHARE] of the pager's width toward the next ([toward] 1) or the last page, then a release. */
    private fun swipe(fromTag: String, toTag: String, toward: Int) {
        from = fromTag
        to = toTag
        val pager = rule.onNodeWithTag("underline.pager")
        val step = pager.fetchSemanticsNode().size.width * DRAG_SHARE / DRAG_STEPS
        zero = rule.mainClock.currentTime
        frame("rest")
        pager.performTouchInput { down(center) }
        repeat(DRAG_STEPS) { index ->
            pager.performTouchInput { moveBy(Offset(-toward * step, 0f), delayMillis = FRAME_MS) }
            rule.mainClock.advanceTimeByFrame()
            if (index % 2 == 1) frame("drag ${(index + 1) * 100 * DRAG_SHARE.toDouble() / DRAG_STEPS}".substringBefore('.') + "%")
        }
        pager.performTouchInput { up() }
        val released = rule.mainClock.currentTime
        for (after in AFTER_RELEASE) {
            rule.mainClock.advanceTimeBy(released + after - rule.mainClock.currentTime)
            frame("up+${rule.mainClock.currentTime - released}")
        }
    }

    /** A tap on [tab]. */
    private fun tap(tab: String, fromTag: String, toTag: String) {
        from = fromTag
        to = toTag
        zero = rule.mainClock.currentTime
        frame("rest")
        rule.onNodeWithTag(tab).performClick()
        for (after in AFTER_TAP) {
            val left = zero + after - rule.mainClock.currentTime
            if (left > 0) rule.mainClock.advanceTimeBy(left)
            frame("tap+${rule.mainClock.currentTime - zero}")
        }
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun frame(label: String) {
        val index = frames.size
        captureScreenRoboImage(directory().resolve("%02d.png".format(index)).absolutePath)
        frames += listOf(index, label, rule.mainClock.currentTime - zero, x("underline.tabs.indicator"), x(from), x(to)).joinToString("\t")
    }

    /** Where the first node tagged [tag] is in the root, or `-` while there is none (the page is not composed). */
    private fun x(tag: String): String =
        rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.positionInRoot?.x?.toString() ?: "-"

    private fun directory(): File = File(checkNotNull(System.getProperty("user.dir")))
        .resolve("build/tab-swipe-strips/$device/$strip")
        .also { it.mkdirs() }

    private companion object {
        const val SETTLE_MS = 1_500L
        const val FRAME_MS = 16L
        const val DRAG_STEPS = 6
        const val DRAG_SHARE = 0.6f

        /** Clock after the finger lifts: the settle is 300 ms, and today's right pane swaps after it. */
        val AFTER_RELEASE = listOf(48L, 112L, 208L, 320L, 480L)

        /** Clock after a tap: a fade through is out by 90 ms and in by 300. */
        val AFTER_TAP = listOf(32L, 64L, 96L, 128L, 176L, 240L, 320L, 480L)
    }
}
