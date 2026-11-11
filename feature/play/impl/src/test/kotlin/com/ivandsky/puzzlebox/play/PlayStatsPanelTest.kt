package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.auth.GoogleIdToken
import com.ivandsky.puzzlebox.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.data.leaderboard.SubmitStatus
import com.ivandsky.puzzlebox.data.leaderboard.SubmitStatusTracker
import com.ivandsky.puzzlebox.data.linkedin.LinkState
import com.ivandsky.puzzlebox.data.linkedin.LinkedInDayData
import com.ivandsky.puzzlebox.data.linkedin.LinkedInGhost
import com.ivandsky.puzzlebox.data.linkedin.LinkedInOwner
import com.ivandsky.puzzlebox.data.linkedin.LinkedInOwnState
import com.ivandsky.puzzlebox.designsystem.MedalBronze
import com.ivandsky.puzzlebox.designsystem.MedalGold
import com.ivandsky.puzzlebox.designsystem.MedalSilver
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.ComputeStreak
import com.ivandsky.puzzlebox.domain.GetPuzzleDay
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.ObserveGameStats
import com.ivandsky.puzzlebox.domain.ObserveLeaderboard
import com.ivandsky.puzzlebox.domain.ObservePlayStats
import com.ivandsky.puzzlebox.domain.ObserveTodaySet
import com.ivandsky.puzzlebox.domain.SyncTodayPuzzles
import com.ivandsky.puzzlebox.domain.linkedin.ObserveLinkedInDay
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.CompletionSource
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.game.today
import com.ivandsky.puzzlebox.gameui.board.DailyBoardLine
import com.ivandsky.puzzlebox.gameui.board.DailyBoardRowModel
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.play.board.BoardSignIn
import com.ivandsky.puzzlebox.play.friend.dismissedGoogleSignIn
import com.ivandsky.puzzlebox.play.friend.oneGoogleAccount
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.linkedin.FakeLinkedInDays
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeLeaderboardRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * The tablet play side panel (A15) over fake repositories: the live timer, today's set
 * progress, this game's streak with best and average, and today's board: its top rows with the
 * player's own row pinned under them.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
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
    private val linkedInDays = FakeLinkedInDays()

    private fun viewModel(
        identity: Identity? = me,
        completions: FakeCompletionRepository = this.completions,
        leaderboard: FakeLeaderboardRepository = this.leaderboard,
        slotDay: LocalDate = day,
        idTokens: GoogleIdTokenProvider = dismissedGoogleSignIn,
        linkedIn: Boolean = false,
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
            linkedIn = if (linkedIn) ObserveLinkedInDay(linkedInDays, fakes.games) else null,
        )
    }

    /** The slot day's connections board as read from LinkedIn and held on the phone. */
    private fun cachedConnections(
        vararg ghosts: LinkedInGhost,
        own: LinkedInOwnState = LinkedInOwnState.Unknown,
        owner: LinkedInOwner? = null,
    ) = linkedInDays.emit(
        alpha,
        day,
        LinkedInDayData(
            link = LinkState.Connected("me"),
            delta = 0,
            boardInWindow = true,
            inFlight = false,
            lastSync = null,
            own = own,
            ghosts = ghosts.toList(),
            boardFetchedAtMs = 1L,
            owner = owner,
        ),
    )

    private fun importedTime(seconds: Long) = LinkedInOwnState.Imported(
        CompletionRecord(TestGames.Ref(alpha, PuzzleKey.Daily(day, 7)), seconds * 1_000L, 0, RecordStats(1, "{}"), source = CompletionSource.LinkedIn),
    )

    private fun ghost(name: String, seconds: Long?, hintFree: Boolean = true, photoPath: String? = null) =
        LinkedInGhost("ref-$name", name, seconds?.let { it * 1_000L }, hintFree, flawless = false, photoPath = photoPath)

    /** The [part] inside the row tagged [row], whatever else on the card shares its tag. */
    private fun inRow(row: String, part: String) = hasTestTag(part) and hasAnyAncestor(hasTestTag(row))

    private fun show(vm: PlayStatsViewModel, elapsedMs: Long? = 83_000L) {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                Column {
                    val state by vm.state.collectAsStateWithLifecycle()
                    PlayStatsPanel(state = state, elapsedMs = { elapsedMs }, signIn = BoardSignIn.Idle, onSignIn = {})
                }
            }
        }
        composeRule.waitForIdle()
    }

    /** The panel over [state] as given, frozen: what it draws before any source has emitted. */
    private fun showFrozen(state: PlayStatsState, signIn: BoardSignIn = BoardSignIn.Idle, onSignIn: () -> Unit = {}) {
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
    fun `solved before the own time reaches the board, the card says the time is posting under the rows`() {
        val solvedToday = FakeCompletionRepository().apply { setRecords(listOf(record(alpha, day, 40_000L))) }
        val othersOnly = FakeLeaderboardRepository(mapOf(board to (1..3).map { entry("p$it", "Player $it", it * 10_000L) }))
        show(viewModel(completions = solvedToday, leaderboard = othersOnly))

        composeRule.onNodeWithTag("play.stats.times.note").assertTextEquals("Posting your time…")
        composeRule.onAllNodesWithTag("daily_board.row").assertCountEquals(3)
    }

    @Test
    fun `unsolved, the card shows the rows and no note about the player's own time`() {
        val othersOnly = FakeLeaderboardRepository(mapOf(board to (1..3).map { entry("p$it", "Player $it", it * 10_000L) }))
        show(viewModel(leaderboard = othersOnly))

        composeRule.onAllNodesWithTag("daily_board.row").assertCountEquals(3)
        composeRule.onNodeWithTag("play.stats.times.note").assertDoesNotExist()
    }

    /** Solved, the own row not on the board, the watcher at [status]: the panel. */
    private fun showAwaitingRow(status: SubmitStatus) {
        val solvedToday = FakeCompletionRepository().apply { setRecords(listOf(record(alpha, day, 40_000L))) }
        val othersOnly = FakeLeaderboardRepository(mapOf(board to (1..3).map { entry("p$it", "Player $it", it * 10_000L) }))
        posts.set(me.uid, "${alpha.value}/$day/7", status)
        show(viewModel(completions = solvedToday, leaderboard = othersOnly))
    }

    @Test
    fun `a post in flight says the time is posting`() {
        showAwaitingRow(SubmitStatus.Posting)
        composeRule.onNodeWithTag("play.stats.times.note").assertTextEquals("Posting your time…")
    }

    @Test
    fun `a deferred post says it is saved on this device`() {
        showAwaitingRow(SubmitStatus.Deferred)
        composeRule.onNodeWithTag("play.stats.times.note")
            .assertTextEquals("Saved on this device — it'll post when you're online.")
    }

    @Test
    fun `a rejected post says it could not post`() {
        showAwaitingRow(SubmitStatus.Rejected)
        composeRule.onNodeWithTag("play.stats.times.note").assertTextEquals("Couldn't post this time.")
    }

    @Test
    fun `a posted entry outside the rows says so`() {
        showAwaitingRow(SubmitStatus.Posted)
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
    }

    @Test
    fun `a past day's card says past days aren't ranked`() {
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
                    (1..3).map { DailyBoardLine.Real(DailyBoardRowModel(it, "Player $it", it * 10_000L, hints = 0, isSelf = false, playerId = "player-$it"), it) },
                    solved = true,
                    order = RankOrder.TimeThenHints,
                    post = post,
                ),
            ),
        )
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
    fun `the card shows no tabs, only the top rows`() {
        show(viewModel())

        composeRule.onAllNodesWithTag("play.stats.tab.top").assertCountEquals(0)
        composeRule.onAllNodesWithTag("play.stats.tab.near").assertCountEquals(0)
        composeRule.onNodeWithText("Near you").assertDoesNotExist()
        composeRule.onNodeWithText("Top").assertDoesNotExist()
    }

    @Test
    fun `the first five show with your row pinned under them when it ranks lower`() {
        show(viewModel())

        // Ranks 1-5, then the player's 7th place pinned under them.
        composeRule.onAllNodesWithTag("daily_board.row").assertCountEquals(5)
        composeRule.onNode(inRow("daily_board.row.self", "daily_board.row.rank")).assertTextEquals("7")
        composeRule.onNode(inRow("daily_board.row.self", "daily_board.row.name")).assertTextEquals("You")
        composeRule.onNodeWithTag("leaderboard.you").assertExists()
        composeRule.onNodeWithText("Player 1").assertExists()
        composeRule.onNodeWithText("Player 6").assertDoesNotExist()
    }

    @Test
    fun `your row is not pinned when it is inside the first five`() {
        val inside = FakeLeaderboardRepository(
            mapOf(board to (1..8).map { entry(if (it == 3) "me" else "p$it", "Player $it", it * 10_000L) }),
        )
        show(viewModel(leaderboard = inside))

        composeRule.onAllNodesWithTag("daily_board.row").assertCountEquals(4)
        composeRule.onNode(inRow("daily_board.row.self", "daily_board.row.rank")).assertTextEquals("3")
        composeRule.onAllNodesWithTag("leaderboard.you").assertCountEquals(0)
    }

    @Test
    fun `every row on the card has a photo slot, PuzzleBox rows and the player's own alike`() {
        show(viewModel())

        composeRule.onAllNodesWithTag("daily_board.row.photo").assertCountEquals(6)
    }

    @Test
    fun `connections rank into the rows by the combined place and are marked as LinkedIn`() {
        cachedConnections(ghost("Ada Lin", 25), ghost("Cy Park", 65))
        show(viewModel(linkedIn = true))

        // Player 1, 2, Ada (3rd), Player 3, 4, then the player's 9th place pinned under them.
        composeRule.onAllNodesWithTag("daily_board.row").assertCountEquals(4)
        composeRule.onAllNodesWithTag("daily_board.ghost").assertCountEquals(1)
        composeRule.onNodeWithTag("daily_board.ghost.name").assertTextEquals("Ada Lin")
        composeRule.onNodeWithTag("daily_board.ghost.rank").assertTextEquals("3")
        composeRule.onNodeWithTag("daily_board.ghost.linkedin_dot").assertExists()
        composeRule.onNode(inRow("daily_board.row.self", "daily_board.row.rank")).assertTextEquals("9")
        composeRule.onNode(inRow("daily_board.row.self", "daily_board.row.puzzlebox_rank")).assertTextEquals("#7 on PuzzleBox")
        composeRule.onNodeWithText("Cy Park").assertDoesNotExist()
    }

    private fun placeCell(row: Int) = composeRule.onAllNodesWithTag("daily_board.row.rank")[row]

    @Test
    fun `with LinkedIn on the podium places are medals`() {
        cachedConnections()
        show(viewModel(linkedIn = true))

        assertTrue(placeCell(0).showsMedal(MedalGold))
        assertTrue(placeCell(1).showsMedal(MedalSilver))
        assertTrue(placeCell(2).showsMedal(MedalBronze))
    }

    @Test
    fun `with LinkedIn off no row draws a medal`() {
        show(viewModel(linkedIn = false))

        placeCell(0).assertTextEquals("1")
        listOf(MedalGold, MedalSilver, MedalBronze).forEach { medal ->
            (0..2).forEach { row -> assertFalse("row $row has a $medal medal", placeCell(row).showsMedal(medal)) }
        }
    }

    @Test
    fun `the player's imported LinkedIn time replaces their PuzzleBox time and moves their place`() {
        // The player's own 70s is 7th on PuzzleBox; 15s from LinkedIn is 2nd, so they join the top five and are not pinned.
        cachedConnections(own = importedTime(15))
        show(viewModel(linkedIn = true))

        composeRule.onAllNodesWithTag("daily_board.row").assertCountEquals(4)
        composeRule.onNode(inRow("daily_board.row.self", "daily_board.row.rank")).assertTextEquals("2")
        composeRule.onNode(inRow("daily_board.row.self", "daily_board.row.score")).assertTextEquals("Perfect · 0:15")
        composeRule.onNodeWithText("Player 5").assertDoesNotExist()
    }

    @Test
    fun `a connection named like a player is that player at the LinkedIn time and is drawn once`() {
        cachedConnections(ghost(" player  3 ", 5))
        show(viewModel(linkedIn = true))

        composeRule.onAllNodesWithTag("daily_board.ghost").assertCountEquals(0)
        composeRule.onAllNodesWithText("Player 3").assertCountEquals(1)
        composeRule.onAllNodesWithTag("daily_board.row.name")[0].assertTextEquals("Player 3")
        composeRule.onAllNodesWithTag("daily_board.row.rank")[0].assertTextEquals("1")
    }

    private val tablet = Identity(uid = "tablet", displayName = "Tablet", photoUrl = null)

    private fun youRow(vm: PlayStatsViewModel) =
        (vm.state.value.times as PlayTimes.Board).rows.filterIsInstance<DailyBoardLine.Real>().single { it.row.isSelf }

    @Test
    fun `with no row of this profile the row named like the owner is the you row at the LinkedIn time with the photo`() {
        cachedConnections(own = importedTime(15), owner = LinkedInOwner(" player  7 ", "/cache/owner.jpg"))
        val vm = viewModel(identity = tablet, linkedIn = true)
        show(vm)

        assertEquals("Player 7", youRow(vm).row.name)
        assertEquals("/cache/owner.jpg", youRow(vm).row.photo)
        composeRule.onNode(inRow("daily_board.row.self", "daily_board.row.rank")).assertTextEquals("2")
        composeRule.onNode(inRow("daily_board.row.self", "daily_board.row.score")).assertTextEquals("Perfect · 0:15")
    }

    @Test
    fun `with no row of this profile and no name match the owner's LinkedIn row is added and ranks by its time`() {
        cachedConnections(own = importedTime(15), owner = LinkedInOwner("Owner Name", "/cache/owner.jpg"))
        val vm = viewModel(identity = tablet, linkedIn = true)
        show(vm)

        assertTrue(youRow(vm).row.fromLinkedIn)
        assertEquals("/cache/owner.jpg", youRow(vm).row.photo)
        assertEquals("Owner Name", youRow(vm).row.name)
        composeRule.onNode(inRow("daily_board.row.self", "daily_board.row.rank")).assertTextEquals("2")
        composeRule.onAllNodes(inRow("daily_board.row.self", "daily_board.row.puzzlebox_rank")).assertCountEquals(0)
    }

    @Test
    fun `a row of this profile stays the you row and a same-name row is left alone`() {
        cachedConnections(own = importedTime(15), owner = LinkedInOwner("Player 3", "/cache/owner.jpg"))
        val vm = viewModel(linkedIn = true)
        show(vm)

        assertEquals("Player 7", youRow(vm).row.name)
        assertEquals("/cache/owner.jpg", youRow(vm).row.photo)
        composeRule.onNode(inRow("daily_board.row.self", "daily_board.row.rank")).assertTextEquals("2")
        composeRule.onAllNodesWithText("Player 3").assertCountEquals(1)
    }

    @Test
    fun `a hinted connection shows its time alone and ranks behind hint free times in a hints first game`() {
        cachedConnections(ghost("Dee Moss", 5, hintFree = false))
        val fewTimes = FakeLeaderboardRepository(mapOf(board to (1..3).map { entry("p$it", "Player $it", it * 10_000L) }))
        show(viewModel(leaderboard = fewTimes, linkedIn = true))

        // Player 3 is last of the hint-free times, so the hinted 0:05 comes after it.
        composeRule.onNodeWithTag("daily_board.ghost.score").assertTextEquals("0:05")
        composeRule.onNodeWithTag("daily_board.ghost.rank").assertTextEquals("4")
    }

    @Test
    fun `a connection with a hidden time is left out of the times`() {
        cachedConnections(ghost("Ada Lin", null))
        show(viewModel(linkedIn = true))

        composeRule.onAllNodesWithTag("daily_board.ghost").assertCountEquals(0)
        composeRule.onAllNodesWithTag("daily_board.row").assertCountEquals(5)
    }

    @Test
    fun `with LinkedIn off cached connections stay out of the times`() {
        cachedConnections(ghost("Ada Lin", 25))
        show(viewModel(linkedIn = false))

        composeRule.onAllNodesWithTag("daily_board.ghost").assertCountEquals(0)
        composeRule.onAllNodesWithTag("daily_board.row").assertCountEquals(5)
        composeRule.onNode(inRow("daily_board.row.self", "daily_board.row.rank")).assertTextEquals("7")
    }

    @Test
    fun `a day with no connections draws the board alone and never asks LinkedIn for any`() {
        show(viewModel(linkedIn = true))

        composeRule.onAllNodesWithTag("daily_board.ghost").assertCountEquals(0)
        composeRule.onAllNodesWithTag("daily_board.row").assertCountEquals(5)
        assertTrue(linkedInDays.syncCalls.isEmpty())
    }

    @Test
    fun `a connection's cached photo reaches its row`() {
        cachedConnections(ghost("Ada Lin", 25, photoPath = "/cache/ada-lin.jpg"), ghost("Cy Park", 65))
        val vm = viewModel(linkedIn = true)
        show(vm)

        val ghosts = (vm.state.value.times as PlayTimes.Board).rows.filterIsInstance<DailyBoardLine.Ghost>()
        assertEquals(mapOf("Ada Lin" to "/cache/ada-lin.jpg", "Cy Park" to null), ghosts.associate { it.row.name to it.row.photo })
    }

    @Test
    fun `signed out the board asks for sign-in instead of rows`() {
        show(viewModel(identity = null))

        composeRule.onAllNodesWithTag("daily_board.row").assertCountEquals(0)
        composeRule.onNodeWithTag("custom_board.sign_in").assertExists()
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
        composeRule.onAllNodesWithTag("daily_board.row").assertCountEquals(5)
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
