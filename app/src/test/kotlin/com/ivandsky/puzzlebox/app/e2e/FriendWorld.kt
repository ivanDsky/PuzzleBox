package com.ivandsky.puzzlebox.app.e2e

import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import com.ivandsky.puzzlebox.app.leaderboard.CustomBoardSubmitter
import com.ivandsky.puzzlebox.app.leaderboard.LeaderboardSubmitWatcher
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.create.api.CreateKey
import com.ivandsky.puzzlebox.data.di.dataModule
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.data.model.Draft
import com.ivandsky.puzzlebox.data.model.PuzzleRefKeys
import com.ivandsky.puzzlebox.data.model.TestSolve
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.CustomBoardRepository
import com.ivandsky.puzzlebox.data.repository.DraftRepository
import com.ivandsky.puzzlebox.data.repository.PublishedPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.ReceivedPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.SharedPuzzleRepository
import com.ivandsky.puzzlebox.domain.GameHistoryDay
import com.ivandsky.puzzlebox.domain.GameStats
import com.ivandsky.puzzlebox.domain.GetPuzzleDay
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.ObserveGameStats
import com.ivandsky.puzzlebox.domain.ObserveTodaySet
import com.ivandsky.puzzlebox.domain.TodayEntry
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.games.queens.QueensDefinition
import com.ivandsky.puzzlebox.games.queens.QueensPuzzleRef
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.PlaySessionStore
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import com.ivandsky.puzzlebox.testing.repository.FakeCustomBoardRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDraftRepository
import com.ivandsky.puzzlebox.testing.repository.FakePublishedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeReceivedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.koin.android.ext.koin.androidContext
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.robolectric.RuntimeEnvironment

internal const val FRIEND_TIMEOUT_MS = 10_000L

/**
 * Two signed-in users on one phone over the real shell's graph, with only [flags] on (an editor, plus NavV2 for a flag-on run):
 * the author's Studio publishes through [shared]; the friend opens the code into [received] and
 * wins into a file-backed Room record store, which stamps first wins as the app does, so the
 * started [CustomBoardSubmitter] posts the win to [boards] and [LeaderboardSubmitWatcher] posts
 * the dailies to the graph's daily board. [close] after the run.
 */
internal class FriendWorld(vararg flags: ReleaseFlag) {

    val drafts = FakeDraftRepository()
    val shared = FakeSharedPuzzleRepository()
    val published = FakePublishedPuzzleRepository()
    val received = FakeReceivedPuzzleRepository()
    val boards = FakeCustomBoardRepository()

    private var room: KoinApplication? = null

    val graph = E2eKoinGraph(
        E2E_TODAY,
        overrides = module {
            single<DraftRepository> { drafts }
            single<SharedPuzzleRepository> { shared }
            single<PublishedPuzzleRepository> { published }
            single<ReceivedPuzzleRepository> { received }
            single<CustomBoardRepository> { boards }
            single<CompletionRepository> {
                val registry = get<GameRegistry>()
                koinApplication {
                    androidContext(RuntimeEnvironment.getApplication())
                    modules(dataModule, module { single { registry } })
                }.also { room = it }.koin.get()
            }
        },
        releaseFlags = FakeReleaseFlags(*flags),
    )

    fun close() {
        room?.close()
    }

    /** The author signs in, keeps [board] as a ready, test-solved draft and publishes it from the Studio: its code. */
    fun publishAsAuthor(robot: ShellRobot, board: EditorDraft<*>, title: String): ShareCode = with(robot) {
        graph.identity.currentIdentity = Identity(uid = AUTHOR, displayName = "Ivan", photoUrl = null)
        shared.signedInUid = AUTHOR
        runBlocking { drafts.upsert(readyDraft(board, title)) }
        tapTab(CreateKey)
        tap("create.draft.row")
        awaitTag("create.readiness.ready")
        tap("create.action.publish")
        awaitTag("create.published.code")
        val code = published.rows.keys.single()
        assertEquals("the shared doc is the draft's game", board.gameId.value, shared.docs.getValue(code).gameId)
        assertEquals("the shared doc is the author's", AUTHOR, shared.docs.getValue(code).authorUid)
        code
    }

    /** The phone's user becomes the friend: a second signed-in account. */
    fun signInFriend() {
        graph.identity.currentIdentity = Identity(uid = FRIEND, displayName = "Friend", photoUrl = null)
        shared.signedInUid = FRIEND
    }

    /**
     * Queens dailies solved on the shell's Today and on the puzzle day and the day before (a streak), the submitters
     * started as the app starts them, and the in-window dailies posted to the daily board: what
     * every daily surface shows before the friend plays, with [game]'s stats among them.
     */
    fun settleDailies(robot: ShellRobot, game: GameId): Dailies = with(robot) {
        // The shell's Today is pinned; the streak counts back from the clock's puzzle day.
        val puzzleDay = graph.koin.get<GetPuzzleDay>()()
        val days = setOf(E2E_TODAY, puzzleDay, puzzleDay.minusDays(1))
        val dailies = days.map { day ->
            CompletionRecord(
                puzzleRef = QueensPuzzleRef(PuzzleKey.Daily(day, QUEENS_NUMBER)),
                time = 90_000L,
                hints = 0,
                stats = RecordStats(schemaVersion = 1, blob = "{}"),
            )
        }
        val completions = graph.koin.get<CompletionRepository>()
        runBlocking { dailies.forEach { completions.onCompleted(it) } }
        graph.koin.get<LeaderboardSubmitWatcher>().start()
        graph.koin.get<CustomBoardSubmitter>().start()
        // A7: only today and yesterday reach the daily board.
        val window = setOf(E2E_TODAY, E2E_TODAY.minusDays(1))
        val inWindow = dailies.map { PuzzleRefKeys.encode(it.puzzleRef) }
            .filter { LeaderboardRef.fromPuzzleRefKey(it)!!.day in window }
        rule.waitUntil(FRIEND_TIMEOUT_MS) { graph.leaderboard.submitted.size >= inWindow.size }
        assertEquals("each in-window daily posted once", inWindow.sorted(), graph.leaderboard.submitted.map { it.puzzleRef }.sorted())
        dailies(game)
    }

    /** Every daily surface's read now: Today, History, stats overall and for Queens and [game], daily records and daily-board posts. */
    fun dailies(game: GameId): Dailies = runBlocking {
        val stats = graph.koin.get<ObserveGameStats>()
        Dailies(
            today = graph.koin.get<ObserveTodaySet>()().first(),
            history = graph.koin.get<ObserveGameHistory>()().first(),
            stats = listOf(null, QueensDefinition.id, game).associateWith { stats(it).first() },
            records = records().filter { it.puzzleRef.key is PuzzleKey.Daily },
            submits = graph.leaderboard.submitted.toList(),
        )
    }

    fun records(): List<CompletionRecord> = runBlocking { graph.koin.get<CompletionRepository>().observeRecords().first() }

    data class Dailies(
        val today: List<TodayEntry>,
        val history: List<GameHistoryDay>,
        val stats: Map<GameId?, GameStats>,
        val records: List<CompletionRecord>,
        val submits: List<LeaderboardEntry>,
    )

    companion object {
        const val AUTHOR = "author"
        const val FRIEND = "friend"
        private const val QUEENS_NUMBER = 918
    }
}

/** A ready draft of [board] titled [title], test-solved by its author, as the Studio stores it. */
internal fun readyDraft(board: EditorDraft<*>, title: String) = Draft(
    id = "draft",
    gameId = board.gameId,
    variant = board.variant,
    draftFormatVersion = board.editor.draftFormatVersion,
    blob = board.encode(),
    title = title,
    updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
    testSolve = TestSolve(board.layoutKey, timeMs = 102_000, hints = 0),
    reservedCode = null,
    imported = false,
    authorName = "",
    note = "",
)

/** From the library's list: Enter code, type [code], and wait for its card. */
internal fun ShellRobot.openByCode(code: ShareCode) {
    tap("library.enterCode")
    rule.onNodeWithTag("friend.code.field").performTextInput(code.display)
    tap("friend.code.open")
    awaitTag("friend.card.title")
    assertRoute("route.friend")
}

/** The live controller of [slot]'s session once it is a [T] and [ready]. */
internal inline fun <reified T : Any> ShellRobot.awaitController(slot: PlaySlot, crossinline ready: (T) -> Boolean): T {
    val store = graph.koin.get<PlaySessionStore>()
    rule.waitUntil(FRIEND_TIMEOUT_MS) { rule.runOnIdle { (store.peek(slot)?.controller as? T)?.let(ready) } == true }
    return rule.runOnIdle { store.peek(slot)?.controller as T }
}

/** Waits for a friend win's result. A bare waitUntil drives no frame here; each poll pumps one so the win's handoff runs. */
internal fun ShellRobot.awaitCustomResult() {
    rule.waitUntil(FRIEND_TIMEOUT_MS) {
        rule.runOnIdle { }
        rule.onAllNodesWithTag("route.custom_result", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }
    assertRoute("route.custom_result")
}
