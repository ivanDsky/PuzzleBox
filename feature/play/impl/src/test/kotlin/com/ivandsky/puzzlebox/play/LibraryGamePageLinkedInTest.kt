package com.ivandsky.puzzlebox.play

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.ArchiveProgress
import com.ivandsky.puzzlebox.domain.GameStats
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.domain.linkedin.LinkedInBoardView
import com.ivandsky.puzzlebox.domain.linkedin.LinkedInDayStatus
import com.ivandsky.puzzlebox.domain.linkedin.LinkedInDayView
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.game.linkedin.LinkedInRead.Rejected.Kind
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.GameCardModel
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.play.linkedin.LinkedInCardState
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The game page draws the LinkedIn lines on its day card only when handed a card state, and collects them only while it is composed. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h800dp")
class LibraryGamePageLinkedInTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val today = LocalDate.parse("2026-11-05")
    private val view = MutableStateFlow<LinkedInDayView?>(null)
    private var resumes = 0
    private var reconnects = 0
    private var retries = 0

    private val row = LibraryRowModel(
        card = GameCardModel(
            game = GameId("zip"),
            name = "Zip",
            brand = GameBrand(AccentSlot.Blue, GameGlyphs.Zip),
            status = TodayStatus.NotStarted,
            rankOrder = RankOrder.TimeThenHints,
            puzzleNumber = 10,
            best = null,
        ),
        today = 10,
        progress = ArchiveProgress(today.minusDays(9)..today, 10, 0, 0),
        stats = GameStats(0, 0, 0, 0, null, null, null, RankOrder.TimeThenHints),
        rules = emptyList(),
    )

    private fun card() = LinkedInCardState(view, onResume = { resumes++ }, onRetry = { retries++ }, onReconnect = { reconnects++ })

    private fun show(card: LinkedInCardState?, shown: () -> Boolean = { true }) {
        composeRule.setContent {
            PuzzleBoxTheme {
                if (shown()) {
                    LibraryGamePage(
                        row = row,
                        page = null,
                        month = null,
                        layout = PlayLayout.SinglePane,
                        calendar = CalendarActions(),
                        onPlay = { _, _ -> },
                        onRetry = {},
                        onOpenLesson = {},
                        onBack = null,
                        offline = false,
                        hasLesson = false,
                        linkedIn = card,
                    )
                }
            }
        }
    }

    private fun status(status: LinkedInDayStatus) = LinkedInDayView(status, LinkedInBoardView.Hidden)

    @Test
    fun withoutTheFlagNoLinkedInLineIsDrawn() {
        view.value = status(LinkedInDayStatus.Imported(95_000L))
        show(card = null)
        composeRule.onAllNodes(
            SemanticsMatcher("a linkedin node") {
                it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("linkedin.") == true
            },
            useUnmergedTree = true,
        ).assertCountEquals(0)
    }

    @Test
    fun theViewIsCollectedOnlyWhileTheCardIsShown() {
        assertEquals(0, view.subscriptionCount.value)
        var visible by mutableStateOf(true)
        show(card = card(), shown = { visible })
        composeRule.waitForIdle()
        assertEquals(1, view.subscriptionCount.value)
        visible = false
        composeRule.waitForIdle()
        assertEquals(0, view.subscriptionCount.value)
    }

    @Test
    fun aPageWithoutTheFlagNeverCollectsTheView() {
        show(card = null)
        composeRule.waitForIdle()
        assertEquals(0, view.subscriptionCount.value)
    }

    @Test
    fun anImportedDayShowsFromLinkedIn() {
        view.value = status(LinkedInDayStatus.Imported(95_000L))
        show(card())
        // The card is one click target, which merges its text into a single node.
        composeRule.onNodeWithTag("linkedin.status", useUnmergedTree = true).assertTextEquals("From LinkedIn · counts toward your streak")
    }

    @Test
    fun resumeReconnectAndRetryReachTheirCallbacks() {
        view.value = status(LinkedInDayStatus.Paused(Kind.Challenge))
        show(card())
        composeRule.onNodeWithTag("linkedin.resume").performClick()
        assertEquals(1, resumes)

        view.value = status(LinkedInDayStatus.SessionExpired)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("linkedin.reconnect").performClick()
        assertEquals(1, reconnects)

        view.value = status(LinkedInDayStatus.Offline)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("linkedin.retry").performClick()
        assertEquals(1, retries)
    }
}
