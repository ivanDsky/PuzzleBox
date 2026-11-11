package com.ivandsky.puzzlebox.play.linkedin

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.data.linkedin.LinkedInGhost
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.linkedin.LinkedInBoardView
import com.ivandsky.puzzlebox.domain.linkedin.LinkedInDayStatus
import com.ivandsky.puzzlebox.domain.linkedin.LinkedInDayView
import com.ivandsky.puzzlebox.game.linkedin.LinkedInRead.Rejected.Kind
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.play.libraryCardColors
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** What the day card's LinkedIn lines say and offer in each state, and how the board reads. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h800dp")
class LinkedInDayLinesTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var resumes = 0
    private var reconnects = 0
    private var retries = 0
    private var current by mutableStateOf(LinkedInDayView(LinkedInDayStatus.Idle, LinkedInBoardView.Hidden))

    private fun show(view: LinkedInDayView, reconnect: Boolean = true) {
        current = view
        composeRule.setContent {
            PuzzleBoxTheme {
                LinkedInDayLines(
                    view = current,
                    colors = libraryCardColors(GameBrand(AccentSlot.Blue, GameGlyphs.Zip)),
                    nowMs = NOW,
                    onResume = { resumes++ },
                    onReconnect = if (reconnect) ({ reconnects++ }) else null,
                    onRetry = { retries++ },
                )
            }
        }
    }

    private fun status(status: LinkedInDayStatus, board: LinkedInBoardView = LinkedInBoardView.Hidden) = LinkedInDayView(status, board)

    private fun shown(
        yourRank: Int? = null,
        total: Int = 12,
        ghosts: List<LinkedInGhost> = emptyList(),
        updatedAtMs: Long = NOW - FIVE_MINUTES_MS,
    ) = LinkedInBoardView.Shown(ghosts, yourRank, total, updatedAtMs)

    private fun ghost(name: String, timeMs: Long?, hintFree: Boolean = true) =
        LinkedInGhost("member-$name", name, timeMs, hintFree, flawless = false)

    private fun tag(tag: String) = composeRule.onNodeWithTag(tag)

    private fun texts(node: SemanticsNodeInteraction): List<String> =
        node.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }

    @Test
    fun notConnectedDrawsNothing() {
        show(status(LinkedInDayStatus.NotConnected, shown(yourRank = 3, ghosts = listOf(ghost("Ana", 41_000L)))))
        composeRule.onAllNodes(SemanticsMatcher("has a linkedin tag") {
            it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("linkedin.") == true
        }, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun importedSaysItCountsTowardTheStreak() {
        show(status(LinkedInDayStatus.Imported(52_000L)))
        tag("linkedin.status").assertTextEquals("From LinkedIn · counts toward your streak")
    }

    @Test
    fun sessionExpiredOffersReconnectOnly() {
        show(status(LinkedInDayStatus.SessionExpired))
        tag("linkedin.reconnect").assertExists().performClick()
        assertEquals(1, reconnects)
        tag("linkedin.resume").assertDoesNotExist()
        tag("linkedin.retry").assertDoesNotExist()
    }

    @Test
    fun sessionExpiredWithoutALoginRouteOffersNothing() {
        show(status(LinkedInDayStatus.SessionExpired), reconnect = false)
        tag("linkedin.status").assertTextEquals("Your LinkedIn session ended")
        tag("linkedin.reconnect").assertDoesNotExist()
    }

    @Test
    fun pausedOffersResumeAndNeverRetry() {
        show(status(LinkedInDayStatus.Paused(Kind.Challenge)))
        val reasons = listOf(Kind.Challenge, Kind.RateLimited, Kind.Unexpected).map { kind ->
            current = status(LinkedInDayStatus.Paused(kind))
            composeRule.waitForIdle()
            tag("linkedin.resume").assertExists().performClick()
            tag("linkedin.retry").assertDoesNotExist()
            texts(tag("linkedin.status")).single()
        }
        assertEquals(3, reasons.toSet().size)
        assertEquals(3, resumes)
        assertEquals(0, retries)
    }

    @Test
    fun pausedBySessionExpiryReadsAsReconnect() {
        show(status(LinkedInDayStatus.Paused(Kind.SessionExpired)))
        tag("linkedin.status").assertTextEquals("Your LinkedIn session ended")
        tag("linkedin.reconnect").assertExists()
        tag("linkedin.resume").assertDoesNotExist()
    }

    @Test
    fun offlineOffersRetry() {
        show(status(LinkedInDayStatus.Offline))
        tag("linkedin.retry").performClick()
        assertEquals(1, retries)
        tag("linkedin.resume").assertDoesNotExist()
    }

    @Test
    fun loadingDrawsTheSkeletonAndNoAction() {
        show(status(LinkedInDayStatus.Loading))
        tag("linkedin.skeleton").assertExists()
        tag("linkedin.retry").assertDoesNotExist()
    }

    @Test
    fun rankLineReadsYoureThirdOfTwelve() {
        show(status(LinkedInDayStatus.Imported(52_000L), shown(yourRank = 3, total = 12)))
        tag("linkedin.rank").assertTextEquals("You're 3rd of 12 connections")
    }

    @Test
    fun ordinalFollowsEnglish() {
        val expected = mapOf(
            1 to "1st", 2 to "2nd", 3 to "3rd", 4 to "4th", 11 to "11th", 12 to "12th", 13 to "13th",
            21 to "21st", 22 to "22nd", 23 to "23rd", 101 to "101st", 111 to "111th",
        )
        expected.forEach { (n, text) -> assertEquals(text, ordinal(n)) }
    }

    @Test
    fun unrankedBoardCountsConnections() {
        show(status(LinkedInDayStatus.DidNotPlay, shown(yourRank = null, total = 12)))
        tag("linkedin.rank").assertTextEquals("12 connections played")
    }

    @Test
    fun aSingleUnrankedConnectionReadsInTheSingular() {
        show(status(LinkedInDayStatus.DidNotPlay, shown(yourRank = null, total = 1)))
        tag("linkedin.rank").assertTextEquals("1 connection played")
    }

    @Test
    fun emptyBoardSaysNoneOfYourConnectionsPlayed() {
        show(status(LinkedInDayStatus.DidNotPlay, shown(total = 0, ghosts = emptyList())))
        tag("linkedin.empty").assertTextEquals("None of your connections played")
        tag("linkedin.updated").assertDoesNotExist()
        tag("linkedin.rank").assertDoesNotExist()
        tag("linkedin.ghost").assertDoesNotExist()
    }

    @Test
    fun outsideTheWindowSaysLastSevenDays() {
        show(status(LinkedInDayStatus.Imported(52_000L), LinkedInBoardView.OutsideWindow))
        tag("linkedin.window").assertTextEquals("Connections' boards cover the last 7 days.")
    }

    @Test
    fun hiddenBoardDrawsNoBoardLines() {
        show(status(LinkedInDayStatus.DidNotPlay, LinkedInBoardView.Hidden))
        tag("linkedin.window").assertDoesNotExist()
        tag("linkedin.rank").assertDoesNotExist()
    }

    @Test
    fun cardKeepsTheSummaryAndNeverListsConnections() {
        val ghosts = listOf(ghost("Ana", 41_000L), ghost("Jonas", 63_000L), ghost("Omar", null))
        show(status(LinkedInDayStatus.Imported(52_000L), shown(yourRank = 2, total = 4, ghosts = ghosts)))
        tag("linkedin.rank").assertTextEquals("You're 2nd of 4 connections")
        tag("linkedin.updated").assertExists()
        tag("linkedin.ghost").assertDoesNotExist()
        composeRule.onAllNodesWithText("Ana", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Jonas", substring = true).assertCountEquals(0)
    }

    @Test
    fun updatedStampReadsFiveMinutesAgo() {
        show(status(LinkedInDayStatus.DidNotPlay, shown(total = 2, updatedAtMs = NOW - FIVE_MINUTES_MS)))
        tag("linkedin.updated").assertTextEquals("Updated 5 min ago")
    }

    @Test
    fun updatedStampReadsJustNowUnderAMinute() {
        show(status(LinkedInDayStatus.DidNotPlay, shown(total = 2, updatedAtMs = NOW - 59_000L)))
        tag("linkedin.updated").assertTextEquals("Updated just now")
    }

    @Test
    fun updatedStampStepsThroughHoursAndDays() {
        assertEquals("Updated 2 h ago", updatedText(NOW, NOW - 2 * 60 * 60_000L))
        assertEquals("Updated 3 d ago", updatedText(NOW, NOW - 3 * 24 * 60 * 60_000L))
        assertEquals("Updated just now", updatedText(NOW, NOW + 60_000L))
    }

    private companion object {
        const val NOW = 1_800_000_000_000L
        const val FIVE_MINUTES_MS = 300_000L
    }
}
