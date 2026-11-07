package com.ivandsky.puzzlebox.app.e2e

import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.data.di.dataModule
import com.ivandsky.puzzlebox.data.model.SharedPuzzleDoc
import com.ivandsky.puzzlebox.data.repository.ReceivedPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.SavedPlayRepository
import com.ivandsky.puzzlebox.data.repository.SharedPuzzleRepository
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.SessionBlob
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.encodeShare
import com.ivandsky.puzzlebox.game.editor.shareFormatVersion
import com.ivandsky.puzzlebox.games.queens.QueensDefinition
import com.ivandsky.puzzlebox.games.queens.QueensSession
import com.ivandsky.puzzlebox.games.queens.QueensVariants
import com.ivandsky.puzzlebox.games.queens.editor.QueensDraft
import com.ivandsky.puzzlebox.games.queens.editor.QueensEditor
import com.ivandsky.puzzlebox.games.queensui.QueensPlayController
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.PlaySessionStore
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import com.ivandsky.puzzlebox.testing.repository.FakeReceivedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * A13 over the real shell and a file-backed Room database: a friend's Queens puzzle played for
 * a while, then killed without saved state and reopened offline, opens its card over a saved
 * time (the in-progress time the card's Resume reads) equal to the time it was left at, and
 * its Play resumes the same marks and time. Today's Queens, in progress at the same time in
 * its own slot, keeps its own marks and time.
 */
@RunWith(RobolectricTestRunner::class)
// Plain Application: the merged manifest's PuzzleBoxApplication would start its own Koin.
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w411dp-h891dp")
class FriendResumeE2ETest {

    private val shared = FakeSharedPuzzleRepository()
    private val received = FakeReceivedPuzzleRepository()

    /** One data graph per simulated process; each opens the same database file. */
    private val processes = mutableListOf<KoinApplication>()

    @After
    fun closeProcesses() {
        processes.forEach(KoinApplication::close)
    }

    /**
     * The E2E graph with the production Room [SavedPlayRepository], so only the saves that
     * reached the disk survive a [ShellRobot.relaunch]; the shared and received fakes are the
     * network and the received puzzles' disk.
     */
    private val graph = E2eKoinGraph(
        E2E_TODAY,
        overrides = module {
            single<SharedPuzzleRepository> { shared }
            single<ReceivedPuzzleRepository> { received }
            single<SavedPlayRepository> {
                koinApplication {
                    androidContext(RuntimeEnvironment.getApplication())
                    modules(dataModule)
                }.also(processes::add).koin.get()
            }
        },
        releaseFlags = FakeReleaseFlags(ReleaseFlag.FriendSharing),
    )

    @Test
    fun `a friend puzzle killed mid-play resumes offline from its card with its marks and time`() = shellRobot(graph = graph) {
        publishFriendPuzzle()
        tapTab(PlayKey)

        // Today's Queens, left mid-play: its own slot, its own time.
        openGame(QUEENS)
        play(dailySlot, cells = DAILY_MARKS, ms = DAILY_PLAY_MS)
        back()
        awaitSaved(dailySlot, DAILY_PLAY_MS)
        val dailyLeft = savedSession(dailySlot)
        tap(LIBRARY_PAGE_BACK)

        openCard(code)
        tap(CARD_PLAY)
        awaitTag(GAME)
        play(friendSlot, cells = FRIEND_MARKS, ms = FRIEND_PLAY_MS)

        background()
        awaitSaved(friendSlot, FRIEND_PLAY_MS)
        // Stopped, the clock is folded into the saved session: this is what the kill leaves.
        val friendLeft = savedSession(friendSlot)
        foreground()

        // The process dies; the next one has no network.
        shared.online = false
        relaunch()
        tapTab(PlayKey)
        openCard(code)

        // The card's Resume reads the friend slot's saved time; today's slot keeps its own.
        val inProgress = runBlocking { saves.observeInProgress().first() }
        assertTime("the card resumes at the time it was left", friendLeft.elapsedMs, inProgress[friendSlot])
        assertTime("today's Queens keeps its own time", dailyLeft.elapsedMs, inProgress[dailySlot])

        tap(CARD_PLAY)
        awaitTag(GAME)
        val friendResumed = session(friendSlot)
        assertEquals("the friend's puzzle, not today's", friendLeft.ref, friendResumed.ref)
        assertEquals("the same marks", friendLeft.marks, friendResumed.marks)
        assertTime("the same time", friendLeft.elapsedMs, friendResumed.elapsedMs)

        back()
        awaitTag(CARD_TITLE)
        tap(CARD_UP)
        openGame(QUEENS)
        val dailyResumed = session(dailySlot)
        assertEquals("today's marks are untouched", dailyLeft.marks, dailyResumed.marks)
        assertTime("today's time is untouched", dailyLeft.elapsedMs, dailyResumed.elapsedMs)
    }

    /** A friend's unique 6×6 Queens board, published to the shared fake under a fresh code. */
    private fun publishFriendPuzzle() {
        val board = EditorDraft(QueensEditor, DraftState(QueensDraft(SIZE, regions(UNIQUE))))
        shared.signedInUid = AUTHOR
        runBlocking {
            shared.publish(
                SharedPuzzleDoc(
                    code = code,
                    formatVersion = SharedPuzzleDoc.FORMAT_VERSION,
                    gameId = QUEENS,
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
    private fun ShellRobot.openCard(code: ShareCode) {
        tap(ENTER_CODE)
        rule.onNodeWithTag(CODE_FIELD).performTextInput(code.display)
        tap(CODE_OPEN)
        awaitTag(CARD_TITLE)
        assertRoute(FRIEND)
    }

    /** Marks [cells] once each on [slot]'s live board, then lets [ms] of play pass. */
    private fun ShellRobot.play(slot: PlaySlot, cells: List<Int>, ms: Long) {
        session(slot)
        val startedAt = System.currentTimeMillis()
        rule.runOnIdle { cells.forEach { checkNotNull(controller(slot).board).onTap(it) } }
        assertEquals("one mark per tap", cells.toSet(), session(slot).marks.keys.mapTo(HashSet()) { it.idx })
        // Queens' clock is the wall clock.
        rule.waitUntil(ms + TIMEOUT_MS) { System.currentTimeMillis() - startedAt >= ms }
    }

    /** Waits until [slot]'s row holding at least [playedMs] is on disk; Room commits on its own executor. */
    private fun ShellRobot.awaitSaved(slot: PlaySlot, playedMs: Long) {
        rule.waitUntil(TIMEOUT_MS) { runBlocking { saves.observeInProgress().first() }[slot]?.let { it >= playedMs } == true }
    }

    /** [slot]'s session as the disk holds it. */
    private fun ShellRobot.savedSession(slot: PlaySlot): QueensSession {
        val envelope = checkNotNull(SessionBlob.decode(checkNotNull(runBlocking { saves.load(slot) })))
        val plugin = checkNotNull(graph.koin.get<GameRegistry>()[QueensDefinition.id])
        return plugin.session.deserialize(envelope.session).getOrNull() as QueensSession
    }

    /** [slot]'s live session once its board is ready. */
    private fun ShellRobot.session(slot: PlaySlot): QueensSession {
        rule.waitUntil(TIMEOUT_MS) { (store.peek(slot)?.controller as? QueensPlayController)?.board != null }
        return checkNotNull(rule.runOnIdle { controller(slot).board?.session })
    }

    private fun ShellRobot.controller(slot: PlaySlot): QueensPlayController =
        checkNotNull(store.peek(slot)) { "no live play of $slot" }.controller as QueensPlayController

    private fun assertTime(message: String, expectedMs: Long, actualMs: Long?) {
        assertEquals("$message (±1 s)", expectedMs.toDouble(), checkNotNull(actualMs) { message }.toDouble(), 1_000.0)
    }

    private val ShellRobot.store: PlaySessionStore get() = graph.koin.get()
    private val ShellRobot.saves: SavedPlayRepository get() = graph.koin.get()

    private val code = checkNotNull(ShareCode.parse(CODE))
    private val friendSlot = PlaySlot.Custom(QueensDefinition.id, code.value)
    private val dailySlot = PlaySlot.Daily(QueensDefinition.id, E2E_TODAY)

    /** Row-major region ids from letter rows ('A' = 0), space-separated. */
    private fun regions(rows: String): List<Int> = rows.filter { it != ' ' }.map { it - 'A' }

    private companion object {
        const val QUEENS = "queens"
        const val AUTHOR = "author"
        const val CODE = "K7Q2MX"
        const val SIZE = 6

        /** One solution on a 6×6 board. */
        const val UNIQUE = "BBBBAA BBBBCA EBBDCA EEDDDD EEFFDF EEFFFF"

        /** Off-solution cells on each board: one tap marks a cell, never a queen. */
        val FRIEND_MARKS = listOf(0, 1, 2)
        val DAILY_MARKS = listOf(0, 3)

        // The friend's board plays longer, so the two slots' times can only match by mixing them up.
        const val DAILY_PLAY_MS = 1_000L
        const val FRIEND_PLAY_MS = 2_500L

        const val GAME = "route.game"
        const val FRIEND = "route.friend"
        const val LIBRARY_PAGE_BACK = "library.page.back"
        const val ENTER_CODE = "library.enterCode"
        const val CODE_FIELD = "friend.code.field"
        const val CODE_OPEN = "friend.code.open"
        const val CARD_TITLE = "friend.card.title"
        const val CARD_PLAY = "friend.card.play"
        const val CARD_UP = "friend.up"
        const val TIMEOUT_MS = 5_000L
    }
}
