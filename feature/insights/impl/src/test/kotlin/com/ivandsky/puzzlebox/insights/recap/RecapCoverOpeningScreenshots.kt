package com.ivandsky.puzzlebox.insights.recap

import android.content.Context
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.insights.GOLDEN_PHONE
import com.ivandsky.puzzlebox.insights.GOLDEN_TABLET
import com.ivandsky.puzzlebox.insights.GOLDEN_TABLET_WIDE
import com.ivandsky.puzzlebox.insights.domain.recap.RecapPeriod
import com.ivandsky.puzzlebox.insights.recap.RecapFixtures.fastest
import com.ivandsky.puzzlebox.insights.recap.RecapFixtures.gameName
import com.ivandsky.puzzlebox.insights.recap.RecapFixtures.solved
import com.ivandsky.puzzlebox.insights.recap.RecapFixtures.streak
import java.io.File
import java.time.YearMonth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The cover's opening on a fresh open with motion on, caught mid-deal and as its content comes in: the card rising, the
 * flip, and the lines, title and fan arriving, on phone, tablet and a wide tablet, light and dark. Captures in
 * `snapshots/story-shell/cover-<ms>ms-<device>-<light|dark>.png`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class RecapCoverOpeningScreenshots {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `opening phone light`() = capture("phone", dark = false)

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `opening phone dark`() = capture("phone", dark = true)

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `opening tablet light`() = capture("tablet", dark = false)

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `opening tablet dark`() = capture("tablet", dark = true)

    @Config(qualifiers = GOLDEN_TABLET_WIDE)
    @Test fun `opening wide light`() = capture("wide", dark = false)

    @Config(qualifiers = GOLDEN_TABLET_WIDE)
    @Test fun `opening wide dark`() = capture("wide", dark = true)

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(device: String, dark: Boolean) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            PuzzleBoxTheme(darkTheme = dark) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    RecapScreen(Opening, "October 2026", gameName, onPage = {}, onWatchReplay = {}, onClose = {}, displayName = "Ivan")
                }
            }
        }
        var at = 0L
        for (ms in Frames) {
            rule.mainClock.advanceTimeBy(ms - at)
            at = ms
            val file = File(checkNotNull(System.getProperty("user.dir")))
                .resolve("snapshots/story-shell/cover-${ms}ms-$device-${if (dark) "dark" else "light"}.png")
                .canonicalFile
                .also { checkNotNull(it.parentFile).mkdirs() }
            captureScreenRoboImage(file.absolutePath)
        }
    }
}

private val Opening = RecapUiState.Ready(
    period = RecapPeriod.Month(YearMonth.of(2026, 10)),
    title = "October 2026",
    cards = listOf(solved, fastest, streak),
    initialPage = 0,
)

/** Into the open: the card rising, half-turned, face-up, and the content coming in. */
private val Frames = listOf(150L, 300L, 450L, 1_000L)
