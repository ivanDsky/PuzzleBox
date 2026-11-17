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
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
 * Games, phone and tablet, light and dark: the underline strip with the Duels
 * need-you badge over each of Dailies and Duels. Goldens in `snapshots/underline-tabs/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class LibraryUnderlineTabsGoldenScreenshotsPhone : LibraryUnderlineTabsGoldenScreenshots(PlayLayout.SinglePane, "phone")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_TABLET)
class LibraryUnderlineTabsGoldenScreenshotsTablet : LibraryUnderlineTabsGoldenScreenshots(PlayLayout.TwoPane, "tablet")

abstract class LibraryUnderlineTabsGoldenScreenshots(private val layout: PlayLayout, private val device: String) {

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

    @Test fun `dailies light`() = capture("underline-dailies", GamesTab.Dailies, dark = false)
    @Test fun `dailies dark`() = capture("underline-dailies", GamesTab.Dailies, dark = true)

    @Test fun `duels light`() = capture("underline-duels", GamesTab.Duels, dark = false)
    @Test fun `duels dark`() = capture("underline-duels", GamesTab.Duels, dark = true)

    private fun capture(name: String, tab: GamesTab, dark: Boolean) {
        rule.showGolden(dark) {
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
        rule.mainClock.advanceTimeBy(SETTLE_MS)
        if (tab != GamesTab.Dailies) {
            rule.onNodeWithTag("library.tab.${tab.name.lowercase()}").performClick()
            rule.mainClock.advanceTimeBy(SETTLE_MS)
        }
        // The frame is the tab it names: the Duels golden is the Duels tab selected, not a stale Dailies.
        rule.onNodeWithTag("library.tab.${tab.name.lowercase()}").assertIsSelected()
        rule.settleAndCapture("underline-tabs", name, device, dark)
    }

    private companion object {
        const val SETTLE_MS = 1_000L

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
