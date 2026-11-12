package com.ivandsky.puzzlebox.play.linkedin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.linkedin.LinkedInDayStatus
import com.ivandsky.puzzlebox.game.linkedin.LinkedInRead.Rejected.Kind
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.play.libraryCardColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** What the day card's LinkedIn sync chip says and offers in each state. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h800dp")
class LinkedInSyncChipTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var resumes = 0
    private var reconnects = 0
    private var retries = 0
    private var current by mutableStateOf<LinkedInDayStatus>(LinkedInDayStatus.Idle)

    private fun show(status: LinkedInDayStatus, reconnect: Boolean = true) {
        current = status
        composeRule.setContent {
            PuzzleBoxTheme {
                Box(Modifier.background(libraryCardColors(GameBrand(AccentSlot.Blue, GameGlyphs.Zip)).fill)) {
                    LinkedInSyncChip(
                        status = current,
                        onResume = { resumes++ },
                        onReconnect = if (reconnect) ({ reconnects++ }) else null,
                        onRetry = { retries++ },
                    )
                }
            }
        }
    }

    private fun chip() = composeRule.onNodeWithTag("linkedin.sync")

    @Test
    fun notConnectedDrawsNothing() {
        show(LinkedInDayStatus.NotConnected)
        composeRule.onAllNodes(
            SemanticsMatcher("a linkedin node") { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("linkedin.") == true },
            useUnmergedTree = true,
        ).assertCountEquals(0)
    }

    @Test
    fun loadingShowsARingNotWordsAndIsNotTappable() {
        // The ring spins forever, so the clock cannot settle; step it by hand.
        composeRule.mainClock.autoAdvance = false
        show(LinkedInDayStatus.Loading)
        composeRule.mainClock.advanceTimeBy(500L)
        chip().assertContentDescriptionEquals("Syncing with LinkedIn").assertHasNoClickAction()
        assertNull(chip().fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text))
        composeRule.onNodeWithTag("linkedin.sync.ring", useUnmergedTree = true).assertExists()
    }

    @Test
    fun aSyncedDayShowsOnlyTheMarkAndAllowsNoTap() {
        show(LinkedInDayStatus.Idle)
        for (status in listOf(LinkedInDayStatus.Imported(52_000L, 0), LinkedInDayStatus.DidNotPlay, LinkedInDayStatus.Idle)) {
            current = status
            composeRule.waitForIdle()
            chip().assertContentDescriptionEquals("Synced with LinkedIn").assertHasNoClickAction()
            assertNull(chip().fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text))
        }
    }

    @Test
    fun pausedOffersResumeForEveryReasonButSessionExpiry() {
        show(LinkedInDayStatus.Paused(Kind.Challenge))
        for (kind in listOf(Kind.Challenge, Kind.RateLimited, Kind.Unexpected)) {
            current = LinkedInDayStatus.Paused(kind)
            composeRule.waitForIdle()
            chip().assertTextEquals("Resume").assertHasClickAction().performClick()
        }
        assertEquals(3, resumes)
        assertEquals(0, retries + reconnects)
    }

    @Test
    fun sessionExpiredAsksToSignInAgain() {
        show(LinkedInDayStatus.SessionExpired)
        chip().assertTextEquals("Sign in").assertContentDescriptionEquals("LinkedIn session ended. Sign in again").performClick()
        assertEquals(1, reconnects)

        current = LinkedInDayStatus.Paused(Kind.SessionExpired)
        composeRule.waitForIdle()
        chip().assertTextEquals("Sign in").assertContentDescriptionEquals("LinkedIn session ended. Sign in again").performClick()
        assertEquals(2, reconnects)
        assertEquals(0, resumes + retries)
    }

    @Test
    fun withoutALoginRouteAnExpiredSessionIsOnlyToldNotOffered() {
        show(LinkedInDayStatus.SessionExpired, reconnect = false)
        chip().assertTextEquals("Session ended").assertHasNoClickAction()
    }

    @Test
    fun offlineOffersRetry() {
        show(LinkedInDayStatus.Offline)
        chip().assertTextEquals("Retry").performClick()
        assertEquals(1, retries)
        assertEquals(0, resumes + reconnects)
    }

    @Test
    fun aTappableChipHasATouchTargetOfAtLeast48dp() {
        show(LinkedInDayStatus.Offline)
        chip().assertHeightIsAtLeast(48.dp)
    }
}
