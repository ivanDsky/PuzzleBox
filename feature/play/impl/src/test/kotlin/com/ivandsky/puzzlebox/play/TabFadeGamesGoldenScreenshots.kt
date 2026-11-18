package com.ivandsky.puzzlebox.play

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.gameui.PlayLayout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.KoinApplication
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Games from Dailies to Duels, a finger held 37 % and 62 % of the way, phone and tablet, light and dark: the page under
 * the finger a sixth of the way along and fading out (the tablet's right pane with it), then the next one coming in
 * from the side it comes from. Goldens in `snapshots/tab-fade/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class TabFadeGamesGoldenScreenshotsPhone : TabFadeGamesGoldenScreenshots(PlayLayout.SinglePane, "phone")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_TABLET)
class TabFadeGamesGoldenScreenshotsTablet : TabFadeGamesGoldenScreenshots(PlayLayout.TwoPane, "tablet")

abstract class TabFadeGamesGoldenScreenshots(private val layout: PlayLayout, private val device: String) {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private var koin: KoinApplication? = null

    /** The tablet's first game page carries the game's board, which a view model builds. */
    @Before
    fun startGraph() {
        koin = startGamePageRankKoin(STATE.rows.map { it.card.game })
    }

    @After
    fun stopGraph() {
        koin?.close()
    }

    @Test fun `37 light`() = capture("games-37", 0.37f, dark = false)
    @Test fun `37 dark`() = capture("games-37", 0.37f, dark = true)

    @Test fun `62 light`() = capture("games-62", 0.62f, dark = false)
    @Test fun `62 dark`() = capture("games-62", 0.62f, dark = true)

    private fun capture(name: String, fraction: Float, dark: Boolean) {
        rule.showGolden(dark, animated = true) {
            LibraryScreen(
                state = STATE,
                page = null,
                month = null,
                layout = layout,
                hasLesson = { true },
                onSelect = {},
                onPlay = { _, _ -> },
                onRetry = {},
                onOpenLesson = {},
                calendar = CalendarActions(),
                onOpenRanks = { _, _ -> },
                onEnterCode = {},
                duels = DUELS,
                onMakeOne = {},
            )
        }
        rule.holdSwipeAndCapture(fraction, name, device, dark)
    }

    private companion object {
        val STATE: LibraryUiState = previewState()

        /** A stand-in for the compete module's tab: a few cards in a list of its own; the host owns the side inset. */
        val DUELS = DuelsSlot(
            content = { _, _, _ ->
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    repeat(3) { index ->
                        Surface(Modifier.fillMaxWidth().height(96.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.large) {
                            Text("Duel ${index + 1}", Modifier.padding(Spacing.lg))
                        }
                    }
                }
            },
            needYou = { 3 },
            onOpenDuel = {},
            pane = { modifier -> DuelPaneStandIn(modifier) },
        )
    }
}
