package com.ivandsky.puzzlebox.app.e2e

import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import com.ivandsky.puzzlebox.app.leaderboard.LeaderboardSubmitWatcher
import com.ivandsky.puzzlebox.app.play.ZipPlayFixture
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.create.api.CreateKey
import com.ivandsky.puzzlebox.data.di.dataModule
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.match.ClaimResult
import com.ivandsky.puzzlebox.data.match.CreateResult
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.LegResult
import com.ivandsky.puzzlebox.data.match.LocalRole
import com.ivandsky.puzzlebox.data.match.Match
import com.ivandsky.puzzlebox.data.match.MatchDraft
import com.ivandsky.puzzlebox.data.match.MatchLeg
import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.data.match.ResultPostState
import com.ivandsky.puzzlebox.data.model.PuzzleRefKeys
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.domain.ArchiveProgress
import com.ivandsky.puzzlebox.domain.GameHistoryDay
import com.ivandsky.puzzlebox.domain.GameStats
import com.ivandsky.puzzlebox.domain.GetPuzzleDay
import com.ivandsky.puzzlebox.domain.ObserveArchiveProgress
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.ObserveGameStats
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.gameui.DragEvent
import com.ivandsky.puzzlebox.gameui.GridCell
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.games.zip.ZipPuzzleRef
import com.ivandsky.puzzlebox.games.zipui.ZipPlayController
import com.ivandsky.puzzlebox.history.api.HistoryKey
import com.ivandsky.puzzlebox.history.api.HistoryRootKey
import com.ivandsky.puzzlebox.home.api.HomeKey
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.play.ActivePlay
import com.ivandsky.puzzlebox.play.PlaySessionStore
import com.ivandsky.puzzlebox.play.api.MatchLegKey
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
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
 * Over the real shell, a file-backed Room record store and the daily board's submitter: a
 * duel's day puzzle is that day's official puzzle. Its first solve is the day's solo solve with
 * its duel origin, so history, stats, archive progress and the daily board move as a solo
 * solve's would; a later solo solve of it is a play-again; the leg plays in its own slot, never a
 * daily's saved play; and its result is queued for the poster once.
 */
@RunWith(RobolectricTestRunner::class)
// Plain Application: the merged manifest's PuzzleBoxApplication would start its own Koin.
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w411dp-h891dp")
class OfficialLegE2ETest {

    /** One data graph per simulated process; each opens the same database file. */
    private val processes = mutableListOf<KoinApplication>()

    @After
    fun closeProcesses() {
        processes.forEach(KoinApplication::close)
    }

    /** Room stamps first wins as the app does, so the board submitter sees board-eligible records. */
    private val graph = E2eKoinGraph(
        E2E_TODAY,
        overrides = module {
            single(named(ROOM)) {
                val registry = get<GameRegistry>()
                koinApplication {
                    androidContext(RuntimeEnvironment.getApplication())
                    modules(dataModule, module { single { registry } })
                }.also(processes::add).koin
            }
            single<CompletionRepository> { get<Koin>(named(ROOM)).get() }
        },
        releaseFlags = FakeReleaseFlags(ReleaseFlag.Competitions),
    )

    /** The app's puzzle day: the streak counts back from it, so the duel's puzzles sit on it. */
    private lateinit var today: LocalDate
    private val archiveDay get() = today.minusDays(ARCHIVE_DAYS_BACK)
    private val code = checkNotNull(ShareCode.parse(CODE))
    private val todayLeg get() = MatchLegKey(code.value, 0, ZIP.value)
    private val archiveLeg get() = MatchLegKey(code.value, 1, ZIP.value)
    private val todayRef get() = ZipPuzzleRef(PuzzleKey.Daily(today, TODAY_NUMBER))
    private val dailySlot get() = PlaySlot.Daily(ZIP, today)

    @Test
    fun aLegsFirstSolveIsRecordedAsASoloSolveWithItsDuelOrigin() = duel {
        val before = reads()
        graph.koin.get<LeaderboardSubmitWatcher>().start()

        openDuelPuzzle(todayLeg)
        val result = solveLeg(todayLeg)
        val record = awaitRecord(todayRef)
        assertTrue("the leg's solve is the day's first solve", record.isRecord)
        assertNotNull("a first solve is stamped", record.firstCompletedAt)
        assertEquals("the record holds the leg's solve", result.timeMs to result.hintsUsed, record.time to record.hints)
        assertEquals("the record keeps its duel", code.value, record.matchCode)

        rule.waitUntil(TIMEOUT_MS) { graph.leaderboard.submitted.isNotEmpty() }
        val afterToday = awaitReads(today)
        assertEquals("the day reads solved by the leg", record, afterToday.day(today).record)
        assertEquals("the streak counts the leg's day", 0 to 1, before.streak to afterToday.streak)
        assertEquals("the best is the leg's solve", BestScore(record.time, record.hints), afterToday.stats.getValue(ZIP).best)

        tap(RESULT_NEXT)
        solveLeg(archiveLeg)
        awaitRecord(ZipPuzzleRef(PuzzleKey.Daily(archiveDay, ARCHIVE_NUMBER)))
        val after = awaitReads(archiveDay)
        assertNotEquals("the archive progress moved", before.archive, after.archive)
        assertEquals("stats and archive progress move exactly as solo solves", soloReads().copy(history = after.history), after)

        // The History browser shows both duel days as such.
        tap(RESULT_UP)
        tapTab(HomeKey)
        assertRoute(HISTORY)
        listOf(today, archiveDay).forEach { assertDuelPill(it) }

        // Checked last, after the walk: a late submit of the out-of-window day has had every chance to land.
        assertEquals(
            "only the in-window day reached the daily board, once",
            listOf(PuzzleRefKeys.encode(record.puzzleRef)),
            graph.leaderboard.submitted.map { it.puzzleRef },
        )
    }

    @Test
    fun aLegSolvedInsideTheDailyWindowPostsToTheDailyBoard() = duel {
        graph.koin.get<LeaderboardSubmitWatcher>().start()

        openDuelPuzzle(todayLeg)
        solveLeg(todayLeg)
        val record = awaitRecord(todayRef)

        rule.waitUntil(TIMEOUT_MS) { graph.leaderboard.submitted.isNotEmpty() }
        awaitWallClock(SETTLE_MS) // a second submit would have been launched by now
        assertEquals(
            "the first solve of a leg on today's day posts to that day's board, once",
            listOf(PuzzleRefKeys.encode(record.puzzleRef)),
            graph.leaderboard.submitted.map { it.puzzleRef },
        )
    }

    @Test
    fun aLaterSoloSolveOfALegPuzzleIsAPlayAgain() = duel {
        graph.koin.get<LeaderboardSubmitWatcher>().start()
        openDuelPuzzle(todayLeg)
        solveLeg(todayLeg)
        val record = awaitRecord(todayRef)
        rule.waitUntil(TIMEOUT_MS) { graph.leaderboard.submitted.isNotEmpty() }
        val submitted = graph.leaderboard.submitted.toList()
        val history = awaitReads(today).history

        tap(RESULT_UP)
        openGame(ZIP.value, landing = RESULT_ROUTE)
        awaitActionable(PLAY_AGAIN)
        tap(PLAY_AGAIN)
        awaitTag(GAME_ROUTE)
        val playAgain = awaitDaily { it.zip.session?.path?.isEmpty() == true && !it.controller.isComplete }
        rule.runOnIdle { playAgain.trace(ZipPlayFixture.solution) }
        awaitTag(RESULT_ROUTE)

        val completions = graph.koin.get<CompletionRepository>()
        rule.waitUntil(TIMEOUT_MS) { runBlocking { completions.observePlayAgains(todayRef).first() }.isNotEmpty() }
        val playAgains = runBlocking { completions.observePlayAgains(todayRef).first() }
        assertEquals("the solo solve is one replay of its own", listOf(null), playAgains.map { it.matchCode })
        assertEquals("the leg's record stands", listOf(record), records())
        assertEquals("history is unchanged", history, reads().history)
        awaitWallClock(SETTLE_MS) // a wrongly submitted play-again would have been launched by now
        assertEquals("a replay never reaches the board", submitted, graph.leaderboard.submitted.toList())
    }

    @Test
    fun aLegNeverResumesOrOverwritesADailySavedPlay() = duel {
        openGame(ZIP.value)
        val daily = awaitDaily { it.zip.session != null }
        rule.runOnIdle { daily.trace(MID_GAME) }
        back()
        rule.waitUntil(TIMEOUT_MS) { graph.saved.rows[dailySlot] != null }
        val dailySave = graph.saved.rows.getValue(dailySlot)

        openDuelPuzzle(todayLeg)
        val leg = awaitDuelPlay(todayLeg) { it.zip.session != null }
        assertEquals("the leg starts its own fresh board", emptyList<Cell>(), rule.runOnIdle { leg.zip.session?.path })
        rule.runOnIdle { leg.trace(MID_GAME) }
        background()
        assertTrue("the leg saves to its own slot", graph.saved.rows.keys.any { it is PlaySlot.Leg })
        assertArrayEquals("the daily's save is untouched", dailySave, graph.saved.rows[dailySlot])
        foreground()

        solveDuelPuzzle(todayLeg, ready = { it.zip.session?.path == MID_GAME }) { it.trace(ZipPlayFixture.solution.drop(MID_GAME.size)) }
        assertArrayEquals("the leg's solve leaves the daily's save", dailySave, graph.saved.rows[dailySlot])

        tap(RESULT_UP)
        openGame(ZIP.value)
        val resumed = awaitDaily { it.zip.session != null }
        assertEquals("the daily resumes its own trace", MID_GAME, rule.runOnIdle { resumed.zip.session?.path })
    }

    @Test
    fun aSolvedLegIsQueuedForThePosterOnce() = duel {
        openDuelPuzzle(todayLeg)
        val result = solveLeg(todayLeg)
        val record = awaitRecord(todayRef)
        val queued = runBlocking { graph.outbox.observeAll().first() }
        assertEquals(
            "the solve is queued once, as solved",
            listOf(LegResult(ME, todayLeg.legIndex, result.timeMs, result.hintsUsed, 0L, gaveUp = false) to ResultPostState.Pending),
            queued.map { it.result.copy(completedAtMs = 0L) to it.state },
        )
        assertEquals("the queued solve is the record's", record.time to record.hints, result.timeMs to result.hintsUsed)

        // Opened again, the puzzle is taken: its result shows and nothing is queued again.
        tap(RESULT_UP)
        openDuelPuzzle(todayLeg, landing = RESULT_ROUTE)
        assertEquals("the reopened puzzle shows its result", result, state.currentStack.last())
        assertEquals("nothing more is queued", queued, runBlocking { graph.outbox.observeAll().first() })
        assertEquals("the solve is recorded once", listOf(record), records())
    }

    /** Runs [block] on the Play tab, signed in, with the duel and its two days seeded. */
    private fun duel(block: ShellRobot.() -> Unit) = shellRobot(onPlay, graph = graph) {
        today = graph.koin.get<GetPuzzleDay>()()
        graph.calendar.setAll(today)
        graph.daily.setHistory(
            ZIP,
            listOf(ZipPlayFixture.entry(today, TODAY_NUMBER), ZipPlayFixture.entry(archiveDay, ARCHIVE_NUMBER).copy(archived = true)),
        )
        listOf(today, archiveDay).forEach { graph.daily.setCached(ZIP, it, ZipPlayFixture.envelope()) }
        graph.daily.setArchiveRange(ZIP, archiveDay..today)
        seedDuel()
        block()
    }

    /** Sam's duel over today's puzzle and an archive day's, claimed by me; signed in before any puzzle opens. */
    private fun seedDuel() = runBlocking {
        graph.identity.currentIdentity = Identity(uid = ME, displayName = ME_NAME, photoUrl = null)
        graph.matches.now = Instant.now()
        graph.matches.signedInUid = OPPONENT_UID
        val legs = listOf(LegRef.Daily(ZIP, today, TODAY_NUMBER), LegRef.Daily(ZIP, archiveDay, ARCHIVE_NUMBER))
        val draft = MatchDraft(
            code = code,
            deadlineHours = Match.DEADLINE_CHOICES.first(),
            challenger = Player(OPPONENT_UID, OPPONENT, photoUrl = null),
            invitedUid = null,
            legs = legs.map { MatchLeg(it, authorUid = null) },
            includeFriends = false,
            rematchOf = null,
        )
        check(graph.matches.create(draft) is CreateResult.Created) { "the duel was not created" }
        graph.matches.signedInUid = ME
        check(graph.matches.claim(code, Player(ME, ME_NAME, photoUrl = null)) == ClaimResult.Claimed) { "the duel was not claimed" }
        graph.matchLocal.remember(code, LocalRole.Participant, OPPONENT)
    }

    private fun ShellRobot.solveLeg(key: MatchLegKey) =
        solveDuelPuzzle(key, ready = { it.zip.session != null }) { it.trace(ZipPlayFixture.solution) }

    /** Today's solo Zip once [ready] holds for it. */
    private fun ShellRobot.awaitDaily(ready: (ActivePlay) -> Boolean): ActivePlay {
        val store = graph.koin.get<PlaySessionStore>()
        rule.waitUntil(TIMEOUT_MS) { rule.runOnIdle { store.peek(dailySlot)?.let(ready) } == true }
        return checkNotNull(rule.runOnIdle { store.peek(dailySlot) })
    }

    private val ActivePlay.zip: ZipPlayController get() = controller as ZipPlayController

    /** Drags the play's path on through [cells]. */
    private fun ActivePlay.trace(cells: List<Cell>) =
        cells.forEach { zip.onDragEvent(DragEvent.Extend(GridCell(it.idx / SIZE, it.idx % SIZE))) }

    private fun ShellRobot.records(): List<CompletionRecord> =
        runBlocking { graph.koin.get<CompletionRepository>().observeRecords().first() }

    private fun ShellRobot.awaitRecord(ref: ZipPuzzleRef): CompletionRecord {
        rule.waitUntil(TIMEOUT_MS) { records().any { it.puzzleRef == ref } }
        return records().single { it.puzzleRef == ref }
    }

    /** Lets [ms] of wall clock pass while the UI keeps idling. */
    private fun ShellRobot.awaitWallClock(ms: Long) {
        val since = System.currentTimeMillis()
        rule.waitUntil(ms + TIMEOUT_MS) { System.currentTimeMillis() - since >= ms }
    }

    /** Scrolls History's list to [day]'s Zip row once it shows, and checks it carries the duel pill. */
    private fun ShellRobot.assertDuelPill(day: LocalDate) {
        val key = "${ZIP.value}/$day"
        rule.waitUntil(TIMEOUT_MS) {
            runCatching { rule.onNodeWithTag(HISTORY_LIST).performScrollToNode(hasTestTag("history.row.$key")) }.isSuccess
        }
        // The row's own pill, as the user reads it: the duel and its opponent, not the history data behind it.
        rule.onNode(hasTestTag("history.duel.$key") and hasAnyDescendant(hasText("Solved in a duel vs $OPPONENT")), useUnmergedTree = true)
            .assertExists()
    }

    /** The reads History, Home and Profile render, once [day]'s solve shows in them. */
    private fun ShellRobot.awaitReads(day: LocalDate): Reads {
        rule.waitUntil(TIMEOUT_MS) { reads().history.any { it.gameId == ZIP && it.day == day && it.record != null } }
        return reads()
    }

    /** The merged history, the stats overall and Zip's, and Zip's archive progress, over [history]. */
    private fun ShellRobot.reads(history: ObserveGameHistory = graph.koin.get()): Reads = runBlocking {
        val stats = ObserveGameStats(registry = graph.koin.get(), observeGameHistory = history, computeStreak = graph.koin.get())
        Reads(
            history = history().first(),
            stats = listOf(null, ZIP).associateWith { stats(it).first() },
            archive = ObserveArchiveProgress(graph.daily, graph.calendar, history)(ZIP).first(),
        )
    }

    /** [reads] had every record been a solo solve. */
    private fun ShellRobot.soloReads(): Reads {
        val solo = FakeCompletionRepository().apply { setRecords(records().map { it.copy(matchCode = null) }) }
        return reads(ObserveGameHistory(registry = graph.koin.get(), daily = graph.daily, completions = solo))
    }

    private data class Reads(
        val history: List<GameHistoryDay>,
        val stats: Map<GameId?, GameStats>,
        val archive: ArchiveProgress?,
    ) {
        val streak: Int get() = stats.getValue(null).currentStreak

        fun day(day: LocalDate): GameHistoryDay = history.single { it.gameId == ZIP && it.day == day }
    }

    /** The Play tab is selected; Home's stack holds History, so its tab opens History. */
    private val onPlay = NavigationState(
        tab = PlayKey,
        stacks = listOf(listOf(HomeKey, HistoryKey()), listOf(PlayKey), listOf(HistoryRootKey), listOf(CreateKey)),
    )

    private companion object {
        const val ROOM = "room"
        const val CODE = "K7Q2MX"
        const val ME = "me"
        const val ME_NAME = "Me"
        const val OPPONENT_UID = "sam"
        const val OPPONENT = "Sam"
        const val TODAY_NUMBER = 592
        const val ARCHIVE_NUMBER = 587
        const val ARCHIVE_DAYS_BACK = 5L
        const val SIZE = 5
        val ZIP = ZipDefinition.id
        val MID_GAME: List<Cell> = ZipPlayFixture.solution.take(3)

        const val GAME_ROUTE = "route.game"
        const val RESULT_ROUTE = "route.result"
        const val HISTORY = "route.history"
        const val HISTORY_LIST = "history.list"
        const val RESULT_UP = "result.up"
        const val RESULT_NEXT = "result.next"
        const val PLAY_AGAIN = "result.playAgain"
        const val TIMEOUT_MS = 10_000L
        const val SETTLE_MS = 300L
    }
}
