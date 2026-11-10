package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.auth.GoogleIdToken
import com.ivandsky.puzzlebox.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.data.leaderboard.SubmitStatus
import com.ivandsky.puzzlebox.data.leaderboard.SubmitStatusTracker
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.ComputeStreak
import com.ivandsky.puzzlebox.domain.GetPuzzleDay
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.ObserveGameStats
import com.ivandsky.puzzlebox.domain.ObserveLeaderboard
import com.ivandsky.puzzlebox.domain.ObservePlayStats
import com.ivandsky.puzzlebox.domain.ObserveTodaySet
import com.ivandsky.puzzlebox.domain.SyncTodayPuzzles
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.game.today
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.play.board.BoardSignIn
import com.ivandsky.puzzlebox.play.friend.dismissedGoogleSignIn
import com.ivandsky.puzzlebox.play.friend.oneGoogleAccount
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeLeaderboardRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * The tablet play side panel (A15) over fake repositories: the live timer, today's set
 * progress, this game's streak with best and average, and today's board with its
 * Top | Near you toggle.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1280dp-h1600dp")
class PlayStatsPanelTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val alpha = TestGames.alpha
    private val beta = TestGames.beta
    private val day = TestGames.definition(alpha).cadence.today()
    private val fakes = FakeGames(listOf(alpha, beta), hintsRanked = setOf(alpha))
    private val calendar = FakeGameCalendar(day)
    private val daily = FakeDailyRepository()
    private val completions = FakeCompletionRepository().apply {
        setRecords(
            listOf(
                record(beta, day, 50_000L),
                record(alpha, day.minusDays(1), 60_000L),
                record(alpha, day.minusDays(2), 90_000L),
            ),
        )
    }
    private val me = Identity(uid = "me", displayName = "Ivan", photoUrl = null)
    private val board = LeaderboardRef(alpha.value, day)
    private val leaderboard = FakeLeaderboardRepository(
        mapOf(board to (1..8).map { entry(if (it == 7) "me" else "p$it", "Player $it", it * 10_000L) }),
    )
    private val posts = SubmitStatusTracker()

    private fun viewModel(
        identity: Identity? = me,
        completions: FakeCompletionRepository = this.completions,
        leaderboard: FakeLeaderboardRepository = this.leaderboard,
        slotDay: LocalDate = day,
        idTokens: GoogleIdTokenProvider = dismissedGoogleSignIn,
    ): PlayStatsViewModel {
        val history = ObserveGameHistory(fakes.games, daily, completions)
        val identityService = FakeIdentityService(identity)
        return PlayStatsViewModel(
            slot = PlaySlot.Daily(alpha, slotDay),
            registry = fakes.games,
            brands = fakes.registry,
            calendar = calendar,
            observePlayStats = ObservePlayStats(
                todaySet = ObserveTodaySet(
                    fakes.games, FakeSavedPlayRepository(), completions, calendar, daily,
                    SyncTodayPuzzles(fakes.games, daily, calendar),
                ),
                stats = ObserveGameStats(fakes.games, history, ComputeStreak(GetPuzzleDay(fakes.games))),
                history = history,
                calendar = calendar,
                leaderboard = ObserveLeaderboard(identityService, leaderboard, fakes.games, calendar),
            ),
            posts = posts,
            identity = identityService,
            idTokens = idTokens,
        )
    }

    private fun show(vm: PlayStatsViewModel, elapsedMs: Long? = 83_000L) {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                Column {
                    val state by vm.state.collectAsStateWithLifecycle()
                    PlayStatsPanel(state = state, elapsedMs = { elapsedMs })
                }
            }
        }
        composeRule.waitForIdle()
    }

    /** The panel over [state] as given, frozen: what it draws before any source has emitted. */
    private fun showFrozen(state: PlayStatsState, signIn: BoardSignIn = BoardSignIn.Idle, onSignIn: (() -> Unit)? = null) {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                Column { PlayStatsPanel(state = state, elapsedMs = { null }, signIn = signIn, onSignIn = onSignIn) }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `while the stats load the streak shows a dash, not a zero streak`() {
        showFrozen(viewModel().state.value)

        composeRule.onNodeWithTag("play.stats.streak").assertTextEquals("—-day streak")
    }

    @Test
    fun `solved before the own time reaches the board, near you says the time is posting`() {
        val solvedToday = FakeCompletionRepository().apply { setRecords(listOf(record(alpha, day, 40_000L))) }
        val othersOnly = FakeLeaderboardRepository(mapOf(board to (1..3).map { entry("p$it", "Player $it", it * 10_000L) }))
        show(viewModel(completions = solvedToday, leaderboard = othersOnly))

        composeRule.onNodeWithTag("play.stats.tab.near").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("play.stats.times.note").assertTextEquals("Posting your time…")
    }

    @Test
    fun `unsolved, near you asks to finish first`() {
        val othersOnly = FakeLeaderboardRepository(mapOf(board to (1..3).map { entry("p$it", "Player $it", it * 10_000L) }))
        show(viewModel(leaderboard = othersOnly))

        composeRule.onNodeWithTag("play.stats.tab.near").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("play.stats.times.note").assertTextEquals("Finish to see who's near you")
    }

    /** Solved, the own row not on the board, the watcher at [status]: the panel on Near you. */
    private fun showNearYouAwaitingRow(status: SubmitStatus) {
        val solvedToday = FakeCompletionRepository().apply { setRecords(listOf(record(alpha, day, 40_000L))) }
        val othersOnly = FakeLeaderboardRepository(mapOf(board to (1..3).map { entry("p$it", "Player $it", it * 10_000L) }))
        posts.set(me.uid, "${alpha.value}/$day/7", status)
        show(viewModel(completions = solvedToday, leaderboard = othersOnly))
        composeRule.onNodeWithTag("play.stats.tab.near").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun `a post in flight says the time is posting`() {
        showNearYouAwaitingRow(SubmitStatus.Posting)
        composeRule.onNodeWithTag("play.stats.times.note").assertTextEquals("Posting your time…")
    }

    @Test
    fun `a deferred post says it is saved on this device`() {
        showNearYouAwaitingRow(SubmitStatus.Deferred)
        composeRule.onNodeWithTag("play.stats.times.note")
            .assertTextEquals("Saved on this device — it'll post when you're online.")
    }

    @Test
    fun `a rejected post says it could not post`() {
        showNearYouAwaitingRow(SubmitStatus.Rejected)
        composeRule.onNodeWithTag("play.stats.times.note").assertTextEquals("Couldn't post this time.")
    }

    @Test
    fun `a posted entry outside the rows says so`() {
        showNearYouAwaitingRow(SubmitStatus.Posted)
        composeRule.onNodeWithTag("play.stats.times.note").assertTextEquals("Your time is posted — outside the top 100.")
    }

    /** Solved on a day older than the game's yesterday (A7): the post can never leave, so it is not posting. */
    private fun showPastDayAwaitingRow() {
        val past = day.minusDays(2)
        val solvedPast = FakeCompletionRepository().apply { setRecords(listOf(record(alpha, past, 40_000L))) }
        val othersPast = FakeLeaderboardRepository(
            mapOf(LeaderboardRef(alpha.value, past) to (1..3).map { entry("p$it", "Player $it", it * 10_000L) }),
        )
        show(viewModel(completions = solvedPast, leaderboard = othersPast, slotDay = past))
        composeRule.onNodeWithTag("play.stats.tab.near").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun `a past day's near you says past days aren't ranked`() {
        showPastDayAwaitingRow()
        composeRule.onNodeWithTag("play.stats.times.note").assertTextEquals("Past days aren't ranked.")
    }



    /** Solved, own row missing, post [post]: the note frozen in place. */
    private fun showNoteAwaitingRow(post: SubmitStatus?) {
        showFrozen(
            PlayStatsState(
                gameName = "Zip",
                solvedToday = 1,
                gamesToday = 2,
                streak = 2,
                week = emptyList(),
                best = null,
                rankOrder = RankOrder.TimeThenHints,
                averageMs = null,
                times = PlayTimes.Board(
                    (1..3).map { PlayTimeRow(it, "Player $it", it * 10_000L, hints = 0, you = false) },
                    solved = true,
                    order = RankOrder.TimeThenHints,
                    post = post,
                ),
            ),
        )
        composeRule.onNodeWithTag("play.stats.tab.near").performClick()
        composeRule.waitForIdle()
    }


    @Test
    fun `with no post read, the post note path still says the time is posting`() {
        showNoteAwaitingRow(post = null)
        composeRule.onNodeWithTag("play.stats.times.note").assertTextEquals("Posting your time…")
    }

    @Test
    fun `the panel shows the live timer, today's progress and the game's streak`() {
        show(viewModel())

        composeRule.onNodeWithTag("play.stats.timer").assertTextEquals("1:23")
        composeRule.onNodeWithTag("play.stats.progress").assertTextContains("1/2", substring = true)
        composeRule.onNodeWithTag("play.stats.streak").assertTextEquals("2-day streak")
        composeRule.onNodeWithTag("play.stats.best").assertTextContains("1:00", substring = true)
        composeRule.onNodeWithTag("play.stats.average").assertTextContains("1:15", substring = true)
        composeRule.onNodeWithTag("play.stats.week").assertExists()
    }

    @Test
    fun `top shows the first five with your row pinned, near you shows two either side`() {
        show(viewModel())

        // Top: ranks 1-5, then the player's 7th place pinned under them.
        composeRule.onAllNodesWithTag("play.stats.row").assertCountEquals(5)
        composeRule.onNodeWithTag("play.stats.row.you").assertTextContains("7", substring = true)
        composeRule.onNodeWithText("Player 1").assertExists()

        composeRule.onNodeWithTag("play.stats.tab.near").performClick()
        composeRule.waitForIdle()

        // Near you: ranks 5-8 (the board ends one below the player).
        composeRule.onAllNodesWithTag("play.stats.row").assertCountEquals(3)
        composeRule.onNodeWithTag("play.stats.row.you").assertTextContains("You", substring = true)
        composeRule.onNodeWithText("Player 5").assertExists()
        composeRule.onNodeWithText("Player 1").assertDoesNotExist()

        composeRule.onNodeWithTag("play.stats.tab.top").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag("play.stats.row").assertCountEquals(5)
    }

    @Test
    fun `signed out the board asks for sign-in instead of rows`() {
        show(viewModel(identity = null))

        composeRule.onAllNodesWithTag("play.stats.row").assertCountEquals(0)
        composeRule.onNodeWithTag("play.stats.times.note").assertTextEquals("Sign in on Profile to see today's times")
    }


    /** The signed-out card with the in-place sign-in, its state frozen at [signIn]; [onTap] counts taps. */
    private fun showSignedOutWithSignIn(signIn: BoardSignIn, onTap: () -> Unit) {
        showFrozen(
            PlayStatsState(
                gameName = "Zip",
                solvedToday = null,
                gamesToday = 2,
                streak = null,
                week = emptyList(),
                best = null,
                rankOrder = RankOrder.TimeThenHints,
                averageMs = null,
                times = PlayTimes.SignedOut,
            ),
            signIn = signIn,
            onSignIn = onTap,
        )
    }

    @Test
    fun `signed out with sign-in in place, the card offers Google and a tap starts sign-in`() {
        var taps = 0
        showSignedOutWithSignIn(BoardSignIn.Idle) { taps++ }

        composeRule.onNodeWithTag("custom_board.sign_in").assertIsEnabled().performClick()
        composeRule.waitForIdle()

        assert(taps == 1)
        composeRule.onNodeWithTag("play.stats.times.note").assertDoesNotExist()
    }

    @Test
    fun `while signing in the card's button is busy`() {
        showSignedOutWithSignIn(BoardSignIn.SigningIn) {}

        composeRule.onNodeWithText("Signing in…").assertIsNotEnabled()
    }

    @Test
    fun `a failed sign-in in the card says so`() {
        showSignedOutWithSignIn(BoardSignIn.Failed) {}

        composeRule.onNodeWithTag("custom_board.sign_in.failed").assertExists()
    }

    @Test
    fun `a failed tap in the live panel shows the failure in the card`() {
        val noCredential = object : GoogleIdTokenProvider {
            override suspend fun idToken(activityContext: android.content.Context) =
                GoogleIdToken.Unavailable(GoogleIdToken.Unavailable.Reason.PROVIDER_UNAVAILABLE)
        }
        val vm = viewModel(identity = null, idTokens = noCredential)
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                Column {
                    val state by vm.state.collectAsStateWithLifecycle()
                    val signIn by vm.signIn.collectAsStateWithLifecycle()
                    val context = LocalContext.current
                    PlayStatsPanel(state = state, elapsedMs = { null }, signIn = signIn, onSignIn = { vm.signIn(context) })
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("custom_board.sign_in").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("custom_board.sign_in.failed").assertExists()
    }

    @Test
    fun `signed in on the card's button, the card gives way to the board rows`() {
        val vm = viewModel(identity = null, idTokens = oneGoogleAccount("token"))
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                Column {
                    val state by vm.state.collectAsStateWithLifecycle()
                    val signIn by vm.signIn.collectAsStateWithLifecycle()
                    val context = LocalContext.current
                    PlayStatsPanel(state = state, elapsedMs = { null }, signIn = signIn, onSignIn = { vm.signIn(context) })
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("custom_board.sign_in").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("custom_board.sign_in").assertDoesNotExist()
        composeRule.onAllNodesWithTag("play.stats.row").assertCountEquals(5)
    }

    @Test
    fun `signed out without sign-in in place, the card keeps the Profile note`() {
        showFrozen(
            PlayStatsState(
                gameName = "Zip",
                solvedToday = null,
                gamesToday = 2,
                streak = null,
                week = emptyList(),
                best = null,
                rankOrder = RankOrder.TimeThenHints,
                averageMs = null,
                times = PlayTimes.SignedOut,
            ),
        )

        composeRule.onNodeWithTag("play.stats.times.note").assertTextEquals("Sign in on Profile to see today's times")
        composeRule.onNodeWithTag("custom_board.sign_in").assertDoesNotExist()
    }

    @Test
    fun `a hints-first game's rows lead with hints under its rule`() {
        val hinted = FakeLeaderboardRepository(
            mapOf(board to listOf(entry("p1", "Player 1", 30_000L, hints = 0), entry("p2", "Player 2", 10_000L, hints = 2))),
        )
        show(viewModel(leaderboard = hinted))

        composeRule.onNodeWithText("Fewest hints first, then time").assertExists()
        composeRule.onNodeWithText("Perfect · 0:30").assertExists()
        composeRule.onNodeWithText("2 hints · 0:10").assertExists()
    }

    private fun record(game: com.ivandsky.puzzlebox.model.GameId, on: java.time.LocalDate, timeMs: Long) =
        CompletionRecord(TestGames.Ref(game, PuzzleKey.Daily(on, 7)), timeMs, 0, RecordStats(1, "{}"))

    private fun entry(uid: String, name: String, timeMs: Long, hints: Int = 0) =
        LeaderboardEntry(uid, name, "${alpha.value}/$day/7", timeMs, hints, 0L)
}
