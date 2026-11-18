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
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.data.match.SelfSeats
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

/** The tablet's Games list column: 400 dp wide, so the tab gets exactly that on a tablet. */
private const val GOLDEN_PANE = "w400dp-h800dp-mdpi"
private const val SETTLE_MS = 2_000L

/**
 * The Duels tab body at the two widths it gets, light and dark: every row kind in its section with the challenge card, the selected row the tablet's pane has open,
 * the empty note, the signed-out card, a long opponent name, and a Waiting row with five Finished ones. The phone is the
 * screen less the host's side inset; the pane is the tablet's 400 dp Games column, the same one Dailies and Friends sit
 * in. Goldens in `snapshots/tab/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class DuelsTabPhoneGoldenScreenshots : DuelsTabGoldenScreenshots("phone")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PANE)
class DuelsTabPaneGoldenScreenshots : DuelsTabGoldenScreenshots("pane")

abstract class DuelsTabGoldenScreenshots(private val device: String) {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun `tab populated light`() = tab("tab-populated", POPULATED, dark = false)
    @Test fun `tab populated dark`() = tab("tab-populated", POPULATED, dark = true)

    @Test fun `tab selected light`() = tab("tab-selected", SELECTED, dark = false)
    @Test fun `tab selected dark`() = tab("tab-selected", SELECTED, dark = true)

    @Test fun `tab empty light`() = tab("tab-empty", EMPTY, dark = false)
    @Test fun `tab empty dark`() = tab("tab-empty", EMPTY, dark = true)

    @Test fun `tab waiting and finished light`() = tab("tab-waiting-finished", WAITING_FINISHED, dark = false)
    @Test fun `tab waiting and finished dark`() = tab("tab-waiting-finished", WAITING_FINISHED, dark = true)

    @Test fun `tab signed out light`() = tab("tab-signed-out", EMPTY.copy(signedIn = false), dark = false)
    @Test fun `tab signed out dark`() = tab("tab-signed-out", EMPTY.copy(signedIn = false), dark = true)

    @Test fun `tab long name light`() = tab("tab-long-name", LONG_NAME, dark = false)
    @Test fun `tab long name dark`() = tab("tab-long-name", LONG_NAME, dark = true)

    @Test fun `tab mixed light`() = selfTab("duels-tab-mixed", MIXED, dark = false)
    @Test fun `tab mixed dark`() = selfTab("duels-tab-mixed", MIXED, dark = true)

    @Test fun `tab self only light`() = selfTab("duels-tab-self-only", SELF_ONLY, dark = false)
    @Test fun `tab self only dark`() = selfTab("duels-tab-self-only", SELF_ONLY, dark = true)

    @Test fun `tab finished light`() = selfTab("duels-tab-finished", FINISHED, dark = false)
    @Test fun `tab finished dark`() = selfTab("duels-tab-finished", FINISHED, dark = true)

    private val pane = device == "pane"

    /** The Past you states are named for the contract: the 400 dp column is its "tablet" capture. */
    private fun selfTab(name: String, state: DuelsTabState, dark: Boolean) = tab(name, state, dark, if (pane) "tablet" else "phone")

    private fun tab(name: String, state: DuelsTabState, dark: Boolean, label: String = device) {
        rule.showGolden(dark) {
            // Surface forces its child to full size; the inner Box stands in for the Games host, which insets the
            // phone by Spacing.lg and gives the tablet's pane its whole width, with no inset.
            Box(Modifier.fillMaxSize()) {
                DuelsTabContent(
                    rows = state.rows,
                    signedIn = state.signedIn,
                    duelYourself = state.duelYourself,
                    selfName = state.selfName,
                    inviter = state.inviter,
                    now = { state.now },
                    onNewDuel = {},
                    onOpenDuel = {},
                    onOpenReveal = {},
                    selected = state.selected,
                    modifier = if (pane) Modifier else Modifier.padding(horizontal = Spacing.lg),
                )
            }
        }
        rule.settleAndCapture("tab", name, label, dark)
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

        /** A duel against Past you: no opponent photo, no deadline that matters, `isSelf`. */
        fun self(code: String, kind: DuelRowKind, done: Int, legs: Int) = DuelRow(
            code = code(code),
            opponent = Player(uid = SelfSeats.PAST, name = "Past you", photoUrl = null),
            kind = kind,
            legsDone = done,
            theirDone = legs,
            legs = legs,
            deadline = Instant.MAX,
            isSelf = true,
        )

        val EMPTY = DuelsTabState(rows = emptyList(), signedIn = true, now = NOW, inviter = "Ivan", selfName = "Ivan")

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

        // The tablet's pane has the Your move duel open: its row is the highlighted one.
        val SELECTED = POPULATED.copy(selected = code("SAM222"))

        // Nothing needs me: one open invite waiting, five finished with a draw among them.
        val WAITING_FINISHED = EMPTY.copy(
            rows = listOf(
                row("D5CW81", DuelRowKind.Unclaimed, null, 0, 7, Duration.ofHours(29) + Duration.ofMinutes(38)),
                row("TB1001", DuelRowKind.Finished(1, 0), "Test Bot", 1, 1, Duration.ofHours(30)),
                row("TB2002", DuelRowKind.Finished(0, 2), "Test Bot", 3, 3, Duration.ofHours(40)),
                row("TB3003", DuelRowKind.Finished(3, 0), "Test Bot", 3, 3, Duration.ofHours(50)),
                row("TB4004", DuelRowKind.Finished(2, 1), "Test Bot", 3, 3, Duration.ofHours(60)),
                row("TB5005", DuelRowKind.Finished(1, 1), "Test Bot", 2, 2, Duration.ofHours(70)),
            ),
        )

        // Friend rows and Past you rows side by side, the flag on.
        val MIXED = EMPTY.copy(
            duelYourself = true,
            rows = listOf(
                row("JON333", DuelRowKind.Invited, "Jonas", 0, 3, Duration.ofHours(23)),
                row("SAM222", DuelRowKind.YourMove, "Sam", 1, 3, Duration.ofMinutes(5 * 60 + 12L), theirDone = 2),
                self("SELF11", DuelRowKind.YourMove, 1, 3),
                row("DANA55", DuelRowKind.RevealReady, "Dana", 5, 5, Duration.ofHours(3), theirDone = 5),
                self("SELF22", DuelRowKind.RevealReady, 3, 3),
                row("MAYA44", DuelRowKind.Waiting, "Maya", 3, 3, Duration.ofMinutes(12 * 60 + 5L), theirDone = 1),
                row("K7Q2MX", DuelRowKind.Unclaimed, null, 0, 3, Duration.ofHours(24)),
            ),
        )

        // Signed out with the flag on: only Past you duels, and the challenge card offers them.
        val SELF_ONLY = EMPTY.copy(
            signedIn = false,
            inviter = null,
            duelYourself = true,
            rows = listOf(
                self("SELF11", DuelRowKind.YourMove, 1, 3),
                self("SELF33", DuelRowKind.YourMove, 0, 5),
                self("SELF22", DuelRowKind.RevealReady, 3, 3),
                self("SELF44", DuelRowKind.Finished(2, 1), 3, 3),
                self("SELF55", DuelRowKind.Finished(1, 2), 3, 3),
            ),
        )

        // Finished section: a won and a lost Past you duel among friend ones, a draw too.
        val FINISHED = EMPTY.copy(
            duelYourself = true,
            rows = listOf(
                row("SAM222", DuelRowKind.YourMove, "Sam", 1, 3, Duration.ofMinutes(5 * 60 + 12L), theirDone = 2),
                self("SELF44", DuelRowKind.Finished(2, 1), 3, 3),
                row("RIO777", DuelRowKind.Finished(2, 1), "Rio", 3, 3, Duration.ofHours(30)),
                row("PRI888", DuelRowKind.Finished(0, 2), "Priya", 2, 2, Duration.ofHours(40)),
                row("TB1001", DuelRowKind.Finished(1, 1), "Test Bot", 2, 2, Duration.ofHours(50)),
                self("SELF55", DuelRowKind.Finished(1, 2), 3, 3),
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
internal fun ComposeContentTestRule.showGolden(dark: Boolean, content: @Composable () -> Unit) {
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
 * Rendered with `-Proborazzi.record=true` (untracked review captures in `snapshots/`).
 */
@OptIn(ExperimentalRoborazziApi::class)
internal fun ComposeContentTestRule.settleAndCapture(group: String, name: String, device: String, dark: Boolean) {
    mainClock.advanceTimeBy(SETTLE_MS)
    val file = File(checkNotNull(System.getProperty("user.dir")))
        .resolve("snapshots/$group/$name-$device-${if (dark) "dark" else "light"}.png")
        .canonicalFile
        .also { checkNotNull(it.parentFile).mkdirs() }
    captureScreenRoboImage(file.absolutePath)
}
