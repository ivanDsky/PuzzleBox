package com.ivandsky.puzzlebox.games.strandsui

import android.content.Context
import android.content.res.Configuration
import android.provider.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.gameui.Adopt
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.PlayLayout
import java.io.File
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import com.ivandsky.puzzlebox.game.DailyFetch
import com.ivandsky.puzzlebox.game.FetchResult
import com.ivandsky.puzzlebox.games.strands.feed.NytStrandsDailySource
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

/**
 * Golden matrix for the Strands play screen over a real NYT day (2026-11-06, 6×8, read
 * through the production source and trust gate): idle, mid-trace, a found theme word beside
 * the spangram, both hint stages and the solved board with its rating word, each light and
 * dark, on a phone (single pane) and a tablet (two panes). Robolectric capture, no emulator;
 * recorded by default, verified with `-Proborazzi.verify=true`. Goldens live in
 * `games/strands-ui/snapshots/`.
 *
 * One state per test method, so each window capture is exactly one screen.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-420dpi")
class StrandsGoldenCompactTest : StrandsGoldenTestBase("compact", PlayLayout.SinglePane)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h900dp-420dpi")
class StrandsGoldenExpandedTest : StrandsGoldenTestBase("expanded", PlayLayout.TwoPane)

abstract class StrandsGoldenTestBase(
    private val widthLabel: String,
    private val layout: PlayLayout,
) {

    @get:Rule
    val composeRule = createComposeRule()

    private fun out(name: String, dark: Boolean): String =
        File(checkNotNull(System.getProperty("user.dir")))
            .resolve("snapshots/$name-$widthLabel-${if (dark) "dark" else "light"}.png")
            .canonicalFile
            .also { checkNotNull(it.parentFile).mkdirs() }
            .absolutePath

    /** A frozen clock: every capture reads 0:00, whatever the wall time of the run. */
    private fun controller() = StrandsPlayController(
        PlayEnvironment(CoroutineScope(Dispatchers.Unconfined), {}, {}),
        now = { Instant.EPOCH },
    ).also { assertEquals(Adopt.Adopted, it.adoptToday(RealDay.fetched.puzzle, RealDay.fetched.ref)) }

    private fun StrandsPlayController.play(path: List<Coord>) {
        path.forEach(::tap)
        tap(path.last())
    }

    private fun StrandsPlayController.playNonTheme(words: List<List<Coord>>) = words.forEach { play(it) }

    private fun capture(name: String, dark: Boolean, setUp: StrandsPlayController.() -> Unit = {}) {
        // The reduced-motion path snaps every found-word and hint animation to its end state,
        // so a capture never lands mid-transition.
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        val controller = controller().apply(setUp)
        composeRule.setContent {
            // Board colours read the configuration's night bit, as on a device: set it with
            // the theme so a dark capture is dark through and through.
            val config = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides config) {
                PuzzleBoxTheme(darkTheme = dark) {
                    // The shell paints the page; without it a dark capture sits on a white window.
                    Surface(color = MaterialTheme.colorScheme.background) {
                        StrandsPlayScreen(
                            controller = controller,
                            status = PlayBoardStatus.Ready(banner = null),
                            layout = layout,
                            onRetry = {},
                            onSolved = { _, _ -> },
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage(out(name, dark))
    }

    private fun StrandsPlayController.hintOutlined() {
        playNonTheme(RealDay.firstHintWords)
        pressHint()
    }

    private fun StrandsPlayController.hintOrderRevealed() {
        hintOutlined()
        playNonTheme(RealDay.secondHintWords)
        pressHint()
    }

    private fun StrandsPlayController.solve() = (RealDay.themeWords + listOf(RealDay.spangram)).forEach { play(it) }

    @Test fun `idle light`() = capture("idle", dark = false)
    @Test fun `idle dark`() = capture("idle", dark = true)

    // Two letters of ACORN traced, nothing submitted: the live trace and its slot.
    @Test fun `mid trace light`() = capture("mid-trace", dark = false) { RealDay.themeWords[0].take(2).forEach(::tap) }
    @Test fun `mid trace dark`() = capture("mid-trace", dark = true) { RealDay.themeWords[0].take(2).forEach(::tap) }

    @Test fun `found theme and spangram light`() = capture("found", dark = false) { play(RealDay.themeWords[0]); play(RealDay.spangram) }
    @Test fun `found theme and spangram dark`() = capture("found", dark = true) { play(RealDay.themeWords[0]); play(RealDay.spangram) }

    @Test fun `hint outlined light`() = capture("hint-outlined", dark = false) { hintOutlined() }
    @Test fun `hint outlined dark`() = capture("hint-outlined", dark = true) { hintOutlined() }

    @Test fun `hint order revealed light`() = capture("hint-order", dark = false) { hintOrderRevealed() }
    @Test fun `hint order revealed dark`() = capture("hint-order", dark = true) { hintOrderRevealed() }

    @Test fun `solved with rating light`() = capture("solved", dark = false) { solve() }
    @Test fun `solved with rating dark`() = capture("solved", dark = true) { solve() }
}

/**
 * The committed 2026-11-06 NYT body, adopted as the app would: through [NytStrandsDailySource]
 * and its trust gate. The word paths are that day's real solution, as NYT lists `[row, col]`.
 */
private object RealDay {
    private val day: LocalDate = LocalDate.of(2026, 11, 6)
    private val body: String =
        checkNotNull(javaClass.classLoader?.getResource("fixtures/strands/$day.json")) { "no fixture for $day" }.readText()

    val fetched: DailyFetch = runBlocking {
        when (val result = NytStrandsDailySource { FetchResult.Ok(body) }.fetchDaily(day)) {
            is FetchResult.Ok -> result.value
            is FetchResult.Err -> error("the fixture failed the trust gate: ${result.error}")
        }
    }

    private val wire = JSONObject(body)

    /** In feed order, so the first is ACORN. */
    val themeWords: List<List<Coord>> = wire.getJSONArray("themeWords").let { words ->
        (0 until words.length()).map { cells(wire.getJSONObject("themeCoords").getJSONArray(words.getString(it))) }
    }
    val spangram: List<Coord> = cells(wire.getJSONArray("spangramCoords"))

    /** Valid non-theme words on this board, three earn a hint: ARTS, BRAN, CART… */
    val firstHintWords: List<List<Coord>> = listOf(
        path(1 to 4, 2 to 5, 3 to 5, 4 to 4),
        path(1 to 5, 2 to 5, 1 to 4, 0 to 5),
        path(0 to 4, 1 to 4, 2 to 5, 3 to 5),
    )

    /** …then ALTS, AVER, BACH. */
    val secondHintWords: List<List<Coord>> = listOf(
        path(6 to 4, 5 to 4, 5 to 5, 4 to 4),
        path(6 to 4, 6 to 3, 6 to 2, 7 to 2),
        path(1 to 5, 1 to 4, 0 to 4, 0 to 3),
    )

    private fun path(vararg cells: Pair<Int, Int>) = cells.map { (row, col) -> Coord(row, col) }

    private fun cells(array: JSONArray): List<Coord> =
        (0 until array.length()).map { array.getJSONArray(it).let { cell -> Coord(cell.getInt(0), cell.getInt(1)) } }
}
