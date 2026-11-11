package com.ivandsky.puzzlebox.compete.tab

import android.content.Context
import android.content.res.Configuration
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.domain.DuelRow
import com.ivandsky.puzzlebox.domain.DuelRowKind
import com.ivandsky.puzzlebox.model.ShareCode
import java.io.File
import java.time.Duration
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val GOLDEN_PHONE = "w411dp-h891dp-420dpi"
private const val GOLDEN_TABLET = "w1280dp-h800dp-land-mdpi"
private const val SETTLE_MS = 2_000L

/** The tablet hosts the Duels tab across the Games screen: past the 80 dp rail, inset like the two-pane layout. */
private val TABLET_RAIL = 80.dp

/**
 * The Duels tab body, phone and tablet, light and dark: every row kind in its section with the challenge card, the empty
 * note, the signed-out card, and a long opponent name. The tablet is the wide layout, two columns. Goldens in
 * `snapshots/tab/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class DuelsTabPhoneGoldenScreenshots : DuelsTabGoldenScreenshots("phone")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_TABLET)
class DuelsTabTabletGoldenScreenshots : DuelsTabGoldenScreenshots("tablet")

abstract class DuelsTabGoldenScreenshots(private val device: String) {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun `tab populated light`() = tab("tab-populated", POPULATED, dark = false)
    @Test fun `tab populated dark`() = tab("tab-populated", POPULATED, dark = true)

    @Test fun `tab empty light`() = tab("tab-empty", EMPTY, dark = false)
    @Test fun `tab empty dark`() = tab("tab-empty", EMPTY, dark = true)

    @Test fun `tab signed out light`() = tab("tab-signed-out", EMPTY.copy(signedIn = false), dark = false)
    @Test fun `tab signed out dark`() = tab("tab-signed-out", EMPTY.copy(signedIn = false), dark = true)

    @Test fun `tab long name light`() = tab("tab-long-name", LONG_NAME, dark = false)
    @Test fun `tab long name dark`() = tab("tab-long-name", LONG_NAME, dark = true)

    private val tablet = device == "tablet"

    private fun tab(name: String, state: DuelsTabState, dark: Boolean) {
        rule.showGolden(dark) {
            // Surface forces its child to full size; the inner Box stands in for the Games host, which insets the
            // phone by Spacing.lg and gives the tablet the whole width past the rail.
            Box(Modifier.fillMaxSize()) {
                DuelsTabContent(
                    rows = state.rows,
                    signedIn = state.signedIn,
                    inviter = state.inviter,
                    now = { state.now },
                    onNewDuel = {},
                    onOpenDuel = {},
                    onOpenReveal = {},
                    modifier = if (tablet) {
                        Modifier.padding(start = TABLET_RAIL + Spacing.xl, end = Spacing.xxl)
                    } else {
                        Modifier.padding(horizontal = Spacing.lg)
                    },
                    wide = tablet,
                )
            }
        }
        rule.settleAndCapture("tab", name, device, dark)
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-11-09T12:00:00Z")

        fun code(text: String) = checkNotNull(ShareCode.parse(text))

        fun row(code: String, kind: DuelRowKind, name: String?, done: Int, legs: Int, left: Duration, theirDone: Int = 0) = DuelRow(
            code = code(code),
            opponent = name?.let { Player(uid = "uid-$it", name = it, photoUrl = null) },
            kind = kind,
            legsDone = done,
            theirDone = theirDone,
            legs = legs,
            deadline = NOW + left,
        )

        val EMPTY = DuelsTabState(rows = emptyList(), signedIn = true, now = NOW, inviter = "Ivan")

        val POPULATED = EMPTY.copy(
            rows = listOf(
                row("DANA55", DuelRowKind.RevealReady, "Dana", 5, 5, Duration.ofHours(3)),
                row("SAM222", DuelRowKind.YourMove, "Sam", 1, 3, Duration.ofMinutes(5 * 60 + 12L), theirDone = 2),
                row("JON333", DuelRowKind.Invited, "Jonas", 0, 3, Duration.ofHours(23)),
                row("MAYA44", DuelRowKind.Waiting, "Maya", 3, 3, Duration.ofMinutes(12 * 60 + 5L), theirDone = 1),
                row("K7Q2MX", DuelRowKind.Unclaimed, null, 0, 3, Duration.ofHours(24)),
                row("RIO777", DuelRowKind.Finished(2, 1), "Rio", 3, 3, Duration.ofHours(30)),
                row("LEO888", DuelRowKind.Finished(0, 2), "Leo", 2, 2, Duration.ofHours(40)),
                row("OLD999", DuelRowKind.Finished(null, null), "Nia", 3, 3, Duration.ofHours(50)),
            ),
        )

        val LONG_NAME = EMPTY.copy(
            rows = listOf(
                row("ALEX11", DuelRowKind.YourMove, "Alexandrina Konstantinopolska", 1, 3, Duration.ofMinutes(21 * 60 + 40L), theirDone = 2),
                row("ALEX22", DuelRowKind.RevealReady, "Alexandrina Konstantinopolska", 3, 3, Duration.ofHours(2)),
                row("ALEX33", DuelRowKind.Invited, "Alexandrina Konstantinopolska", 0, 3, Duration.ofHours(23)),
                row("ALEX44", DuelRowKind.Finished(1, 1), "Alexandrina Konstantinopolska", 2, 2, Duration.ofHours(40)),
            ),
        )
    }
}

/**
 * Shows [content] the way the app's root Scaffold does in the [dark] or light theme, system animations off
 * (reduced motion), the compose clock frozen until [settleAndCapture].
 */
private fun ComposeContentTestRule.showGolden(dark: Boolean, content: @Composable () -> Unit) {
    val context = ApplicationProvider.getApplicationContext<Context>()
    Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    mainClock.autoAdvance = false
    setContent {
        val config = Configuration(LocalConfiguration.current).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        CompositionLocalProvider(LocalConfiguration provides config) {
            PuzzleBoxTheme(darkTheme = dark) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() }
            }
        }
    }
}

/**
 * Lets entrances settle, then captures the whole screen to `snapshots/<group>/<name>-<device>-<light|dark>.png`.
 * Recorded with `-Proborazzi.record=true`, verified with `-Proborazzi.verify=true`.
 */
@OptIn(ExperimentalRoborazziApi::class)
private fun ComposeContentTestRule.settleAndCapture(group: String, name: String, device: String, dark: Boolean) {
    mainClock.advanceTimeBy(SETTLE_MS)
    val file = File(checkNotNull(System.getProperty("user.dir")))
        .resolve("snapshots/$group/$name-$device-${if (dark) "dark" else "light"}.png")
        .canonicalFile
        .also { checkNotNull(it.parentFile).mkdirs() }
    captureScreenRoboImage(file.absolutePath)
}
