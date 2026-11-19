package com.ivandsky.puzzlebox.insights.domain.recap

import com.ivandsky.puzzlebox.common.log.FailureLog
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.rankhistory.RankHistoryStore
import com.ivandsky.puzzlebox.data.repository.DailyEntry
import com.ivandsky.puzzlebox.domain.ComputeStreak
import com.ivandsky.puzzlebox.domain.GameHistoryDay
import com.ivandsky.puzzlebox.domain.GetPuzzleDay
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.ranks.OverallState
import com.ivandsky.puzzlebox.domain.ranks.RankWindow
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.CompletionSource
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.game.TestGames.Ref
import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import com.ivandsky.puzzlebox.testing.game.TestGames.beta
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf

/**
 * A fake history for the recap tests: two games, solves of a chosen puzzle day, and the real merged projection
 * ([ObserveGameHistory]) over them, so the tests count what the game page counts. [now] is the clock the streak walk reads.
 */
internal class RecapHistoryFixture(now: Instant = Instant.parse("2026-12-15T12:00:00Z")) {
    val registry = GameRegistry(listOf(TestGames.plugin(alpha), TestGames.plugin(beta)))
    val completions = FakeCompletionRepository()
    val daily = FakeDailyRepository()
    val observeHistory = ObserveGameHistory(registry, daily, completions)

    /** The streak walk the game page uses, "today" being [now]'s puzzle day. */
    val puzzleDay = GetPuzzleDay(registry, Clock.fixed(now, ZoneOffset.UTC))
    val computeStreak = ComputeStreak(puzzleDay)
    val computePeriodFacts = ComputePeriodFacts(computeStreak, ReplaySolveFacts(computeStreak, puzzleDay, registry), registry)

    /** The puzzle day every game reads; move it with `setAll`. */
    val calendar = FakeGameCalendar(LocalDate.of(2026, 11, 20))

    /**
     * The friends read over scripted answers: by default a signed-out viewer, so no friends card; [viewer] signs one
     * in, [friendsOverall] answers each (window, games) read and [rankHistory] each game's kept weeks. [today] is
     * the puzzle day's clock, the recap's settle rule reads it.
     */
    fun resolveFriends(
        viewer: Identity? = null,
        friendsOverall: (RankWindow, List<GameId>) -> Flow<OverallState> = { _, _ -> flowOf(OverallState.SignedOut) },
        rankHistory: (GameId) -> Flow<List<RankHistoryStore.Week>> = { flowOf(emptyList()) },
        today: GetPuzzleDay = puzzleDay,
        failures: FailureLog = FailureLog { _, _, _ -> },
        timeout: Duration = 15.seconds,
    ) = ResolveFriendsStanding(
        identity = FakeIdentityService(viewer),
        friendsOverall = friendsOverall,
        rankHistory = rankHistory,
        puzzleDay = today,
        failures = failures,
        timeout = timeout,
    )

    /** A [GetPuzzleDay] whose puzzle day is the date of [at]. */
    fun puzzleDayAt(at: String) = GetPuzzleDay(registry, Clock.fixed(Instant.parse(at), ZoneOffset.UTC))

    fun availability() = ObserveRecapAvailability(observeHistory, calendar, registry)

    /** Replaces every stored row. */
    fun store(vararg records: CompletionRecord) = completions.setRecords(records.toList())

    suspend fun history(): List<GameHistoryDay> = observeHistory().first()

    /** [game]'s record solve of the puzzle [day]; a null [puzzleNumber] is a practice play. */
    fun solve(game: GameId, day: LocalDate, time: Long = 30_000L, hints: Int = 0, puzzleNumber: Int? = 100) =
        CompletionRecord(
            puzzleRef = Ref(game, PuzzleKey.Daily(day, puzzleNumber)),
            time = time,
            hints = hints,
            stats = RecordStats(schemaVersion = 1, blob = "{}"),
            isRecord = true,
        )

    /** [game]'s [day] captured as an archive day (a past puzzle opened from the archive), not its own day's puzzle. */
    fun captureArchived(game: GameId, day: LocalDate, puzzleNumber: Int? = 100) =
        daily.addEntry(DailyEntry(game, day, puzzleNumber, variant = "", fetchedAtMs = 0L, archived = true))

    /** The same solve as a LinkedIn import stamped at [importedAt], long after its puzzle day. */
    fun imported(game: GameId, day: LocalDate, importedAt: Instant = Instant.parse("2026-11-05T09:00:00Z")) =
        solve(game, day).copy(source = CompletionSource.LinkedIn, firstCompletedAt = importedAt)

    /** An imported play of a day already solved in the app: stored beside the app record, never the day's record row. */
    fun playAgain(game: GameId, day: LocalDate) = imported(game, day).copy(isRecord = false)
}
