package com.ivandsky.puzzlebox.app.e2e

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import com.ivandsky.puzzlebox.app.leaderboard.CustomBoardSubmitter
import com.ivandsky.puzzlebox.app.leaderboard.LeaderboardSubmitWatcher
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.data.di.dataModule
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.model.SharedPuzzleDoc
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.CustomBoardRepository
import com.ivandsky.puzzlebox.data.repository.ReceivedPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.SharedPuzzleRepository
import com.ivandsky.puzzlebox.domain.GameHistoryDay
import com.ivandsky.puzzlebox.domain.GameStats
import com.ivandsky.puzzlebox.domain.GetPuzzleDay
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.ObserveGameStats
import com.ivandsky.puzzlebox.domain.ObserveTodaySet
import com.ivandsky.puzzlebox.domain.TodayEntry
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.encodeShare
import com.ivandsky.puzzlebox.game.editor.shareFormatVersion
import com.ivandsky.puzzlebox.games.queens.QueensDefinition
import com.ivandsky.puzzlebox.games.queens.QueensPuzzleRef
import com.ivandsky.puzzlebox.games.queens.QueensVariants
import com.ivandsky.puzzlebox.games.queens.editor.QueensDraft
import com.ivandsky.puzzlebox.games.queens.editor.QueensEditor
import com.ivandsky.puzzlebox.games.queensui.QueensPlayController
import com.ivandsky.puzzlebox.games.tango.TangoPuzzleRef
import com.ivandsky.puzzlebox.history.api.HistoryKey
import com.ivandsky.puzzlebox.home.api.HomeKey
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.play.PlaySessionStore
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.profile.api.ProfileKey
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import com.ivandsky.puzzlebox.testing.repository.FakeCustomBoardRepository
import com.ivandsky.puzzlebox.testing.repository.FakeReceivedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.Koin
import org.koin.core.KoinApplication
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * SC10 over the real shell, both board submitters and a file-backed Room record store: a
 * signed-in friend solves a friend's Queens puzzle. Its first win posts once to that puzzle's
 * board; the daily board gets nothing new, and Home (Today, streak, bests, recent days),
 * History and the daily reads behind them show exactly what the dailies alone showed.
 */
@RunWith(RobolectricTestRunner::class)
// Plain Application: the merged manifest's PuzzleBoxApplication would start its own Koin.
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w411dp-h891dp")
class FriendWinIsolationE2ETest {

    private val shared = FakeSharedPuzzleRepository()
    private val received = FakeReceivedPuzzleRepository()
    private val boards = FakeCustomBoardRepository()

    /** One data graph per simulated process; each opens the same database file. */
    private val processes = mutableListOf<KoinApplication>()

    @After
    fun closeProcesses() {
        processes.forEach(KoinApplication::close)
    }

    /** Room stamps first wins as the app does, so both submitters see board-eligible records. */
    private val graph = E2eKoinGraph(
        E2E_TODAY,
        releaseFlags = FakeReleaseFlags(ReleaseFlag.FriendSharing),
        overrides = module {
            single<SharedPuzzleRepository> { shared }
            single<ReceivedPuzzleRepository> { received }
            single<CustomBoardRepository> { boards }
            single(named(ROOM)) {
                val registry = get<GameRegistry>()
                koinApplication {
                    androidContext(RuntimeEnvironment.getApplication())
                    modules(dataModule, module { single { registry } })
                }.also(processes::add).koin
            }
            single<CompletionRepository> { get<Koin>(named(ROOM)).get() }
        },
    )

    @Test
    fun `SC10 a signed-in friend win posts to its own board and leaves every daily surface as it was`() =
        shellRobot(historyOverHome, graph = graph) {
            graph.identity.currentIdentity = Identity(uid = FRIEND, displayName = "Friend", photoUrl = null)
            val puzzleDay = graph.koin.get<GetPuzzleDay>()()
            val dailies = seedDailies(puzzleDay)
            publishFriendPuzzle()

            back()
            assertRoute(HOME)
            // Recent days list the days before the puzzle day.
            awaitTag("home.history_row_${puzzleDay.minusDays(1)}")
            val homeBefore = shown(HOME)

            // A fresh process opens on History; the submitters run in it, as the app starts them.
            relaunch()
            assertRoute(HISTORY)
            awaitTag("history.row.tango/$puzzleDay")
            val historyBefore = shown(HISTORY)
            val readsBefore = dailyReads()
            graph.koin.get<LeaderboardSubmitWatcher>().start()
            graph.koin.get<CustomBoardSubmitter>().start()
            rule.waitUntil(TIMEOUT_MS) { graph.leaderboard.submitted.size >= dailies.size }
            awaitWallClock(SETTLE_MS)
            val dailySubmits = graph.leaderboard.submitted.toList()
            assertEquals("each daily record reached its daily board once", dailies.size, dailySubmits.size)

            tapTab(PlayKey)
            openCard()
            tap(CARD_PLAY)
            awaitTag(GAME)
            solveFriendPuzzle()
            assertRoute(CUSTOM_RESULT)

            rule.waitUntil(TIMEOUT_MS) { boards.entries[code]?.isNotEmpty() == true }
            awaitWallClock(SETTLE_MS) // a stray daily submit or a second post would be launched by now
            assertEquals("the friend's board got the one first win", listOf(FRIEND), boards.entries.getValue(code).map { it.uid })
            assertEquals("the daily board got no submit", dailySubmits, graph.leaderboard.submitted.toList())

            tap(RESULT_DONE)
            assertRoute(FRIEND_CARD)
            tapTab(HomeKey)
            assertRoute(HISTORY)
            assertEquals("History is unchanged", historyBefore, shown(HISTORY))
            back()
            assertRoute(HOME)
            assertEquals("Home's Today, streak, bests and recent days are unchanged", homeBefore, shown(HOME))

            val readsAfter = dailyReads()
            assertEquals("Today, History and stats are unchanged", readsBefore, readsAfter)
            assertTrue("the dailies hold a streak", readsAfter.stats.getValue(null).currentStreak >= 2)
            assertEquals(
                "today's Queens is still unplayed",
                TodayStatus.NotStarted,
                readsAfter.today.single { it.game == QueensDefinition.id }.status,
            )
        }

    /**
     * Tango solved on the shell's Today and on the two puzzle days the streak counts back from,
     * and Queens solved slowly the day before Today, so a leaked friend win would move Today,
     * the streak, Queens' best and History. Returns the stored records.
     */
    private fun ShellRobot.seedDailies(puzzleDay: LocalDate): List<CompletionRecord> {
        val tangoDays = setOf(E2E_TODAY, puzzleDay, puzzleDay.minusDays(1))
        val dailies = tangoDays.map { record(TangoPuzzleRef(PuzzleKey.Daily(it, TANGO_NUMBER)), time = 90_000L) } +
            record(QueensPuzzleRef(PuzzleKey.Daily(E2E_TODAY.minusDays(1), QUEENS_NUMBER)), time = 600_000L)
        val completions = graph.koin.get<CompletionRepository>()
        runBlocking { dailies.forEach { completions.onCompleted(it) } }
        return dailies
    }

    /** A friend's unique 6×6 Queens board, published to the shared fake under [code]. */
    private fun publishFriendPuzzle() {
        val board = EditorDraft(QueensEditor, DraftState(QueensDraft(SIZE, regions(UNIQUE))))
        shared.signedInUid = AUTHOR
        runBlocking {
            shared.publish(
                SharedPuzzleDoc(
                    code = code,
                    formatVersion = SharedPuzzleDoc.FORMAT_VERSION,
                    gameId = QueensDefinition.id.value,
                    variant = QueensVariants.variant(SIZE).id.value,
                    shareCodecVersion = checkNotNull(board.shareFormatVersion),
                    payload = checkNotNull(board.encodeShare()),
                    title = "Castle",
                    authorName = "Ivan",
                    note = "",
                    authorUid = AUTHOR,
                    authorTimeMs = 102_000L,
                    authorHints = 0,
                    createdAtMs = 0L,
                    unpublished = false,
                ),
            )
        }
        shared.signedInUid = null
    }

    /** From the library's list: Enter code, then the card once it has opened. */
    private fun ShellRobot.openCard() {
        tap(ENTER_CODE)
        rule.onNodeWithTag(CODE_FIELD).performTextInput(code.display)
        tap(CODE_OPEN)
        awaitTag(CARD_TITLE)
        assertRoute(FRIEND_CARD)
    }

    /** Places the board's one solution, two taps a queen, and waits for the friend result. */
    private fun ShellRobot.solveFriendPuzzle() {
        val store = graph.koin.get<PlaySessionStore>()
        rule.waitUntil(TIMEOUT_MS) { (store.peek(friendSlot)?.controller as? QueensPlayController)?.board != null }
        val board = checkNotNull((store.peek(friendSlot)?.controller as QueensPlayController).board)
        rule.runOnIdle { SOLUTION.forEach { cell -> repeat(2) { board.onTap(cell) } } }
        rule.waitUntil(TIMEOUT_MS) {
            // A bare waitUntil drives no frame here; pump one so the win's handoff runs.
            rule.runOnIdle { }
            rule.onAllNodesWithTag(CUSTOM_RESULT, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** Every tag, text, description and state under [route], in tree order: what the screen shows. */
    private fun ShellRobot.shown(route: String): List<String> {
        rule.waitForIdle()
        return rule.onNodeWithTag(route, useUnmergedTree = true).fetchSemanticsNode().shown()
    }

    private fun SemanticsNode.shown(): List<String> = buildList {
        config.getOrNull(SemanticsProperties.TestTag)?.let { add("#$it") }
        config.getOrNull(SemanticsProperties.Text)?.forEach { add(it.text) }
        config.getOrNull(SemanticsProperties.ContentDescription)?.let(::addAll)
        config.getOrNull(SemanticsProperties.StateDescription)?.let(::add)
        children.forEach { addAll(it.shown()) }
    }

    /** The daily reads Home, History and Profile render: Today's set, the merged history, the stats overall and per game. */
    private fun ShellRobot.dailyReads(): DailyReads = runBlocking {
        val stats = graph.koin.get<ObserveGameStats>()
        DailyReads(
            today = graph.koin.get<ObserveTodaySet>()().first(),
            history = graph.koin.get<ObserveGameHistory>()().first(),
            stats = listOf(null, QueensDefinition.id).associateWith { stats(it).first() },
        )
    }

    private data class DailyReads(
        val today: List<TodayEntry>,
        val history: List<GameHistoryDay>,
        val stats: Map<GameId?, GameStats>,
    )

    /** Lets [ms] of wall clock pass while the UI keeps idling. */
    private fun ShellRobot.awaitWallClock(ms: Long) {
        val since = System.currentTimeMillis()
        rule.waitUntil(ms + TIMEOUT_MS) { System.currentTimeMillis() - since >= ms }
    }

    private fun record(ref: PuzzleRef, time: Long) = CompletionRecord(
        puzzleRef = ref,
        time = time,
        hints = 0,
        stats = RecordStats(schemaVersion = 1, blob = "{}"),
    )

    /** Row-major region ids from letter rows ('A' = 0), space-separated. */
    private fun regions(rows: String): List<Int> = rows.filter { it != ' ' }.map { it - 'A' }

    private val code = checkNotNull(ShareCode.parse(CODE))
    private val friendSlot = PlaySlot.Custom(QueensDefinition.id, code.value)

    /** Home's stack holds History, so a relaunch opens on it and back reaches Home. */
    private val historyOverHome = NavigationState(
        tab = HomeKey,
        stacks = listOf(listOf(HomeKey, HistoryKey()), listOf(PlayKey), listOf(ProfileKey)),
    )

    private companion object {
        const val ROOM = "room"
        const val AUTHOR = "author"
        const val FRIEND = "friend"
        const val CODE = "K7Q2MX"
        const val SIZE = 6
        const val TANGO_NUMBER = 301
        const val QUEENS_NUMBER = 918

        /** One solution on a 6×6 board. */
        const val UNIQUE = "BBBBAA BBBBCA EBBDCA EEDDDD EEFFDF EEFFFF"

        /** UNIQUE's solution as row-major cell indices. */
        val SOLUTION = listOf(5, 7, 16, 20, 24, 33)

        const val HOME = "route.home"
        const val HISTORY = "route.history"
        const val GAME = "route.game"
        const val CUSTOM_RESULT = "route.custom_result"
        const val FRIEND_CARD = "route.friend"
        const val RESULT_DONE = "result.done"
        const val ENTER_CODE = "library.enterCode"
        const val CODE_FIELD = "friend.code.field"
        const val CODE_OPEN = "friend.code.open"
        const val CARD_TITLE = "friend.card.title"
        const val CARD_PLAY = "friend.card.play"
        const val SETTLE_MS = 300L
        const val TIMEOUT_MS = 10_000L
    }
}
