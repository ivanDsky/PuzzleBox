package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.repository.KeptSession
import com.ivandsky.puzzlebox.game.Cadence
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.GameDefinition
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.game.today
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.game.TestGames.Ref
import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import com.ivandsky.puzzlebox.testing.game.TestGames.beta
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeKeptSessionRepository
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Today's set (spec A14): every registered game in registry order, Solved when today's
 * record exists (beats a kept session), InProgress when a session is kept for today,
 * NotStarted otherwise — "today" always the game's cadence day.
 */
class ObserveTodaySetTest {

    private val day = LocalDate.of(2026, 11, 4)

    private fun record(game: GameId, date: LocalDate, time: Long = 41_000L) = CompletionRecord(
        puzzleRef = Ref(game, date, 916),
        time = time,
        hints = 0,
        stats = RecordStats(schemaVersion = 1, blob = "{}"),
    )

    private fun kept(game: GameId, date: LocalDate, elapsedMs: Long = 12_000L) =
        KeptSession(game, date, elapsedMs, byteArrayOf(TestGames.MAGIC))

    private fun todaySet(
        registry: GameRegistry = TestGames.registry(listOf(alpha, beta)),
        records: List<CompletionRecord> = emptyList(),
        kept: List<KeptSession> = emptyList(),
        today: (GameDefinition) -> LocalDate = { day },
    ) = ObserveTodaySet(registry, FakeCompletionRepository(records), FakeKeptSessionRepository(kept), today)()

    private fun entry(game: GameId, status: TodayStatus) =
        TodayEntry(game, game.value.replaceFirstChar(Char::uppercase), status)

    @Test
    fun `nothing played reads NotStarted for every game`() = runTest {
        assertEquals(
            listOf(entry(alpha, TodayStatus.NotStarted), entry(beta, TodayStatus.NotStarted)),
            todaySet().first(),
        )
    }

    @Test
    fun `a session kept for today reads InProgress with its elapsed time`() = runTest {
        val rows = todaySet(kept = listOf(kept(beta, day, elapsedMs = 7_500L))).first()

        assertEquals(
            listOf(entry(alpha, TodayStatus.NotStarted), entry(beta, TodayStatus.InProgress(7_500L))),
            rows,
        )
    }

    @Test
    fun `solved beats a kept session`() = runTest {
        val rows = todaySet(
            records = listOf(record(alpha, day, time = 33_000L)),
            kept = listOf(kept(alpha, day)),
        ).first()

        assertEquals(entry(alpha, TodayStatus.Solved(33_000L)), rows.first())
    }

    @Test
    fun `a stale kept session reads NotStarted`() = runTest {
        val rows = todaySet(kept = listOf(kept(alpha, day.minusDays(1)))).first()

        assertEquals(entry(alpha, TodayStatus.NotStarted), rows.first())
    }

    @Test
    fun `a record from yesterday is not Solved`() = runTest {
        val rows = todaySet(records = listOf(record(alpha, day.minusDays(1)))).first()

        assertEquals(entry(alpha, TodayStatus.NotStarted), rows.first())
    }

    @Test
    fun `registry order is kept`() = runTest {
        val rows = todaySet(
            registry = TestGames.registry(listOf(beta, alpha)),
            records = listOf(record(alpha, day)),
        ).first()

        assertEquals(listOf(beta, alpha), rows.map { it.game })
    }

    @Test
    fun `today is each game's own cadence day`() = runTest {
        val tomorrow = day.plusDays(1)
        val rows = todaySet(
            records = listOf(record(alpha, day), record(beta, day)),
            kept = listOf(kept(beta, tomorrow, elapsedMs = 900L)),
            today = { if (it.id == beta) tomorrow else day },
        ).first()

        assertEquals(
            listOf(entry(alpha, TodayStatus.Solved(41_000L)), entry(beta, TodayStatus.InProgress(900L))),
            rows,
        )
    }

    @Test
    fun `the default today follows the cadence zone, not the device day`() = runTest {
        // UTC+14 and UTC-11 are 25 hours apart: their dates always differ, so one of them
        // differs from the device's day too.
        val east = zoned(alpha, ZoneId.of("Pacific/Kiritimati"))
        val west = zoned(beta, ZoneId.of("Pacific/Pago_Pago"))
        val eastDay = east.definition.cadence.today()
        val westDay = west.definition.cadence.today()

        val rows = ObserveTodaySet(
            games = GameRegistry(listOf(east, west)),
            completions = FakeCompletionRepository(listOf(record(alpha, eastDay), record(beta, eastDay))),
            kept = FakeKeptSessionRepository(listOf(kept(beta, westDay, elapsedMs = 600L))),
        )().first()

        assertEquals(
            listOf(entry(alpha, TodayStatus.Solved(41_000L)), entry(beta, TodayStatus.InProgress(600L))),
            rows,
        )
    }

    /** [game]'s test plugin with its cadence moved to [zone]. */
    private fun zoned(game: GameId, zone: ZoneId): GamePlugin<*, *, *> {
        val base = TestGames.plugin(game)
        val definition = object : GameDefinition by base.definition {
            override val cadence: Cadence = Cadence.Daily(zone)
        }
        return GamePlugin(
            definition = definition,
            rules = base.rules,
            session = base.session,
            score = base.score,
            stateOf = base.stateOf,
            initialSession = base.initialSession,
            refOf = base.refOf,
            replaySession = base.replaySession,
            rendererModel = base.rendererModel,
        )
    }
}
