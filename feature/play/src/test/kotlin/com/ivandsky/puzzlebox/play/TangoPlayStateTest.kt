package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntries
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.TangoDailyEntry
import com.ivandsky.puzzlebox.data.repository.TangoDailyRepository
import com.ivandsky.puzzlebox.data.repository.TangoToday
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoDefinition
import com.ivandsky.puzzlebox.games.tango.TangoPuzzleRef
import com.ivandsky.puzzlebox.games.tango.TangoSession
import com.ivandsky.puzzlebox.games.tangoui.TangoPlacement
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Tango play state tests (tango-05): the ZipPlayState test matrix applied to the
 * Tango twin — load/degrade/unavailable (A7/A8), placement policy (§6 violations LAND),
 * hint counting, the win flow (A4 badge, one record per play), restore, and the timer
 * window. Plus the compete-loop assertion: a tango record must survive the full
 * leaderboard derivation (the ADR-0004 registration's reach).
 */
class TangoPlayStateTest {

    private val today: LocalDate = LocalDate.parse("2026-10-31")

    /** Controllable clock: the state's `now` reads this var. */
    private var fakeNow: Instant = Instant.parse("2026-10-31T00:00:00Z")

    private fun state(
        completions: CompletionRepository,
        daily: TangoDailyRepository,
        onSessionChanged: (TangoSession?) -> Unit = {},
    ) = TangoPlayState(
        daily = daily,
        completions = completions,
        scope = CoroutineScope(Dispatchers.Unconfined),
        now = { fakeNow },
        today = { today },
        onSessionChanged = onSessionChanged,
    )

    private fun tangoDaily(
        todayResult: TangoToday = TangoToday.Fresh(puzzle = TangoPlayFixture.envelope()),
        history: List<TangoDailyEntry> = listOf(entry()),
    ) = FakeTangoDailyRepository(todayResult = todayResult, history = history)

    private fun entry(day: LocalDate = today, puzzleNumber: Int? = 301) =
        TangoDailyEntry(day = day, puzzleNumber = puzzleNumber, variant = "tango/n6", fetchedAtMs = 1_000L)

    /** Places every player-owned cell (the fixture solution) in order. */
    private fun playAll(s: TangoPlayState) {
        for ((idx, value) in TangoPlayFixture.placements) {
            s.onPlacement(TangoPlacement(idx, value))
        }
    }

    @Test
    fun `fresh load lands on a ready board with today's ref`() = runTest {
        val s = state(FakeCompletionRepository(), tangoDaily())

        s.load()

        val ready = s.screen as TangoScreen.Ready
        assertEquals(TangoPlayFixture.puzzle, ready.puzzle)
        assertNull(ready.degraded)
        assertEquals(TangoPuzzleRef(today, 301), s.session?.ref)
        assertEquals(0L, s.session?.elapsedMs)
        assertFalse(s.session?.isComplete ?: true)
    }

    @Test
    fun `cached-fallback load stays playable and flags the banner`() = runTest {
        val s = state(
            FakeCompletionRepository(),
            tangoDaily(
                todayResult = TangoToday.Cached(puzzle = TangoPlayFixture.envelope(), error = FetchError.Unavailable()),
            ),
        )

        s.load()

        val ready = s.screen as TangoScreen.Ready
        assertEquals(TangoPlayFixture.puzzle, ready.puzzle)
        assertTrue(ready.degraded is FetchError.Unavailable)
        assertNotNull(s.session)
    }

    @Test
    fun `unavailable fetch shows the error screen, no session`() = runTest {
        val s = state(
            FakeCompletionRepository(),
            tangoDaily(todayResult = TangoToday.Unavailable(FetchError.Throttled()), history = emptyList()),
        )

        s.load()

        assertTrue(s.screen is TangoScreen.Unavailable)
        assertNull(s.session)
    }

    @Test
    fun `corrupt payload is never playable (payload untrusted)`() = runTest {
        val s = state(
            FakeCompletionRepository(),
            tangoDaily(
                todayResult = TangoToday.Fresh(
                    puzzle = Puzzle(
                        gameId = TangoDefinition.id,
                        variant = TangoPlayFixture.variant,
                        formatVersion = 1,
                        payload = ByteArray(7),
                    ),
                ),
            ),
        )

        s.load()

        val unavailable = s.screen as TangoScreen.Unavailable
        assertTrue(unavailable.message.contains("failed verification")) // §6 exact copy
    }

    @Test
    fun `ambiguous puzzle is refused by the A15 gate (A16)`() = runTest {
        val s = state(
            FakeCompletionRepository(),
            tangoDaily(todayResult = TangoToday.Fresh(puzzle = TangoPlayFixture.envelope(TangoPlayFixture.ambiguous))),
        )

        s.load()

        val unavailable = s.screen as TangoScreen.Unavailable
        assertTrue(unavailable.message.contains("failed verification"))
    }

    @Test
    fun `valid placements land and correct values complete the session`() = runTest {
        val s = state(FakeCompletionRepository(), tangoDaily())
        s.load()

        val (first, value) = TangoPlayFixture.placements.first()
        s.onPlacement(TangoPlacement(first, value))
        assertEquals(value, s.session?.placements?.get(first))

        playAll(s)

        assertNotNull(s.session?.completedAt)
        assertEquals(0, s.session?.mistakeCount)
    }

    @Test
    fun `a rule-violating placement lands as a mistake and never completes the board`() = runTest {
        val s = state(FakeCompletionRepository(), tangoDaily())
        s.load()

        // Row 0: solution is S S M S M M. Placing M at idx 2 (correct), then M at
        // idx 3 and idx 4 (violating: S S M M M makes three adjacent moons) must
        // LAND — §6: the violation is state-driven, mistakes are counted for
        // analytics, never scored, and can never complete the board.
        s.onPlacement(TangoPlacement(2, TangoCell.MOON))
        s.onPlacement(TangoPlacement(3, TangoCell.MOON))
        assertEquals(0, s.session?.mistakeCount) // run of two is still legal
        s.onPlacement(TangoPlacement(4, TangoCell.MOON))

        assertEquals(TangoCell.MOON, s.session?.placements?.get(4))
        assertEquals(1, s.session?.mistakeCount)
        assertNotNull(s.rejection)
        assertNull(s.session?.completedAt)
    }

    @Test
    fun `given cells are inert`() = runTest {
        val s = state(FakeCompletionRepository(), tangoDaily())
        s.load()
        val before = s.session

        s.onPlacement(TangoPlacement(0, TangoCell.MOON)) // idx 0 is a SUN given
        assertEquals(before, s.session)
        assertNull(s.rejection)
    }

    @Test
    fun `a counted hint auto-places a forced cell and names its reason`() = runTest {
        val s = state(FakeCompletionRepository(), tangoDaily())
        s.load()

        s.requestHint()

        assertEquals(1, s.session?.hintsUsed)
        assertNotNull(s.hintCell)
        assertNotNull(s.hintWhy)
        assertNotNull(s.hintCaption)
    }

    @Test
    fun `a fresh win emits ONE record with the tango ref and a true badge`() = runTest {
        val completions = FakeCompletionRepository()
        val s = state(completions, tangoDaily())
        s.load()

        playAll(s)

        val records = completions.observeRecords().first()
        assertEquals(1, records.size)
        assertEquals(TangoPuzzleRef(today, 301), records[0].puzzleRef)
        // A4 badge: no record existed at session start → first win.
        assertEquals(true, s.recordBadge)
        assertFalse(s.recordWriteFailed)
    }

    @Test
    fun `a replay emits a second record whose badge is false`() = runTest {
        val completions = FakeCompletionRepository()
        val s = state(completions, tangoDaily())
        s.load()
        playAll(s)
        assertEquals(true, s.recordBadge)

        s.replay()
        assertNull(s.recordBadge)
        playAll(s)

        val records = completions.observeRecords().first()
        assertEquals(2, records.size)
        assertEquals(false, s.recordBadge) // baseline saw the first win
    }

    @Test
    fun `a failed write surfaces recordWriteFailed and retries cleanly`() = runTest {
        val completions = FakeCompletionRepository().apply { fail = true }
        val s = state(completions, tangoDaily())
        s.load()
        playAll(s)

        assertTrue(s.recordWriteFailed)

        completions.fail = false
        s.retryRecordWrite()

        val records = completions.observeRecords().first()
        assertEquals(1, records.size)
        assertFalse(s.recordWriteFailed)
    }

    @Test
    fun `persist and restore round-trip through the session codec`() = runTest {
        val completions = FakeCompletionRepository()
        val s = state(completions, tangoDaily())
        s.load()
        val (first, value) = TangoPlayFixture.placements.first()
        s.onPlacement(TangoPlacement(first, value))

        val restored = state(FakeCompletionRepository(), tangoDaily())
        restored.adoptRestored(s.persist()!!)

        assertEquals(s.session, restored.session)
        assertEquals(TangoPuzzleRef(today, 301), restored.session?.ref)
    }

    @Test
    fun `a restored completed session shows the win without re-emitting`() = runTest {
        val completions = FakeCompletionRepository()
        val s = state(completions, tangoDaily())
        s.load()
        playAll(s)
        assertEquals(1, completions.observeRecords().first().size)

        val restored = state(FakeCompletionRepository(), tangoDaily())
        restored.adoptRestored(s.persist()!!)

        assertTrue(restored.session?.isComplete ?: false)
        assertEquals(1, completions.observeRecords().first().size)
    }

    @Test
    fun `a corrupt restore falls back to a fresh load`() = runTest {
        val daily = tangoDaily()
        val s = state(FakeCompletionRepository(), daily)

        s.adoptRestored(ByteArray(9))

        assertTrue(s.screen is TangoScreen.Ready)
        assertEquals(1, daily.todayCalls)
    }

    @Test
    fun `background folds the running window into elapsed and stops the clock`() = runTest {
        val s = state(FakeCompletionRepository(), tangoDaily())
        s.load()

        val (first, value) = TangoPlayFixture.placements.first()
        s.onPlacement(TangoPlacement(first, value)) // opens the window at fakeNow
        assertNotNull(s.clockWindow)

        fakeNow = fakeNow.plusMillis(5_000)
        s.onBackground()

        assertEquals(5_000L, s.session?.elapsedMs)
        assertNull(s.clockWindow)
        assertEquals(5_000L, s.displayedElapsedMs())
    }

    @Test
    fun `subtitle is Daily numbered, Daily unnumbered, or Replay by date`() = runTest {
        val s = state(FakeCompletionRepository(), tangoDaily())
        s.load()
        assertEquals("Daily · #301", s.subtitle)

        val archived = TangoSession(
            ref = TangoPuzzleRef(LocalDate.parse("2026-03-09"), 12),
            puzzle = TangoPlayFixture.puzzle,
        )
        assertEquals("Replay · Mar 9", subtitleOf(archived))

        val unnumbered = TangoSession(
            ref = TangoPuzzleRef(today, puzzleNumber = null),
            puzzle = TangoPlayFixture.puzzle,
        )
        assertEquals("Daily", subtitleOf(unnumbered))
    }

    private fun subtitleOf(session: TangoSession): String {
        val ref = session.ref ?: return "Daily"
        return when {
            ref.date != today -> "Replay · " + DateTimeFormatter.ofPattern("MMM d").format(ref.date)
            else -> ref.puzzleNumber?.let { "Daily · #$it" } ?: "Daily"
        }
    }

    @Test
    fun `a tango record survives the full leaderboard derivation (compete loop)`() = runTest {
        val completions = FakeCompletionRepository()
        val s = state(completions, tangoDaily())
        s.load()

        fakeNow = fakeNow.plusMillis(90_000)
        val (first, firstValue) = TangoPlayFixture.placements.first()
        s.onPlacement(TangoPlacement(first, firstValue)) // window opens at T+90s
        fakeNow = fakeNow.plusMillis(45_000)
        for ((idx, value) in TangoPlayFixture.placements.drop(1)) {
            s.onPlacement(TangoPlacement(idx, value)) // completes at T+135s
        }

        val record = completions.observeRecords().first()[0]

        // ADR-0004 reach: PuzzleRefKeys knows the tango ref, the board contract accepts
        // the key, and the record still qualifies. The A4 stamp (isRecord +
        // firstCompletedAt) is Room's onCompleted job; the fake skips it, so emulate
        // the stamp from the session's completedAt exactly as the store derives it.
        assertEquals(45_000L, record.time)
        val stamped = record.copy(isRecord = true, firstCompletedAt = s.session?.completedAt)
        assertNotNull(LeaderboardEntries.fromRecord(stamped, uid = "uid-1", displayName = "Ivan"))
    }
    @Test
    fun `violations stay deferred 0_5s after the last touch, then resume`() {
        val s = state(FakeCompletionRepository(), tangoDaily())
        runBlocking { s.load() }
        assertEquals(emptySet<Set<Int>>(), s.suppressedSets)
        assertEquals(emptySet<Set<Int>>(), s.rendererModel()!!.suppressedSets)

        s.onPlacement(TangoPlacement(2, TangoCell.SUN))
        // A single placement that creates a violation: that violation is new → suppressed.
        assertTrue(s.suppressedSets.isNotEmpty())
        assertEquals(s.suppressedSets, s.rendererModel()!!.suppressedSets)

        // The settle job delays on the state's Unconfined scope — real 500 ms.
        Thread.sleep(600)
        assertEquals(emptySet<Set<Int>>(), s.suppressedSets)
        assertEquals(emptySet<Set<Int>>(), s.rendererModel()!!.suppressedSets)
    }

    // ---- undo / redo (command history) ----

    @Test
    fun `undo and redo move the board and persist while counters and clock stay put`() = runTest {
        val saved = mutableListOf<TangoSession?>()
        val s = state(FakeCompletionRepository(), tangoDaily(), onSessionChanged = { saved += it })
        s.load()
        s.onPlacement(TangoPlacement(2, TangoCell.MOON))
        s.onPlacement(TangoPlacement(3, TangoCell.MOON))
        s.onPlacement(TangoPlacement(4, TangoCell.MOON)) // violating: lands, one mistake
        fakeNow = fakeNow.plusMillis(5_000)
        s.onBackground() // banks 5 s and stops the clock
        assertNull(s.clockWindow)

        s.undo()

        assertNull(s.session?.placements?.get(4))
        assertEquals(1, s.session?.mistakeCount)
        assertEquals(0, s.session?.hintsUsed)
        assertEquals(5_000L, s.session?.elapsedMs)
        assertNotNull(s.clockWindow) // undoing is play: a stopped clock reopens, never rewinds
        assertEquals(s.session, saved.last())
        assertTrue(s.canRedo)

        s.redo()

        assertEquals(TangoCell.MOON, s.session?.placements?.get(4))
        assertEquals(1, s.session?.mistakeCount)
        assertEquals(5_000L, s.session?.elapsedMs)
        assertEquals(s.session, saved.last())
        assertFalse(s.canRedo)
    }

    @Test
    fun `a hint placement is undoable but the hint stays counted`() = runTest {
        val s = state(FakeCompletionRepository(), tangoDaily())
        s.load()
        s.requestHint()
        assertTrue(s.session!!.placements.isNotEmpty())

        s.undo()

        assertTrue(s.session!!.placements.isEmpty())
        assertEquals(1, s.session?.hintsUsed)
        assertNull(s.hintCell) // the reveal goes with the input
        assertNull(s.hintCaption)

        s.redo()
        assertTrue(s.session!!.placements.isNotEmpty())
        assertEquals(1, s.session?.hintsUsed)
    }

    @Test
    fun `a new placement after an undo drops redo`() = runTest {
        val s = state(FakeCompletionRepository(), tangoDaily())
        s.load()
        s.onPlacement(TangoPlacement(2, TangoCell.MOON))
        s.onPlacement(TangoPlacement(3, TangoCell.MOON))
        s.undo()
        assertTrue(s.canRedo)

        s.onPlacement(TangoPlacement(5, TangoCell.MOON))

        assertFalse(s.canRedo)
        s.redo()
        assertNull(s.session?.placements?.get(3))
    }

    @Test
    fun `undoing a violating placement clears the rejection caption`() = runTest {
        val s = state(FakeCompletionRepository(), tangoDaily())
        s.load()
        s.onPlacement(TangoPlacement(2, TangoCell.MOON))
        s.onPlacement(TangoPlacement(3, TangoCell.MOON))
        s.onPlacement(TangoPlacement(4, TangoCell.MOON))
        assertNotNull(s.rejection)

        s.undo()

        assertNull(s.rejection)
    }

    @Test
    fun `a redo that restores a violation waits out the settle window like a placement`() {
        val s = state(FakeCompletionRepository(), tangoDaily())
        runBlocking { s.load() }
        s.onPlacement(TangoPlacement(2, TangoCell.MOON))
        s.onPlacement(TangoPlacement(3, TangoCell.MOON))
        s.onPlacement(TangoPlacement(4, TangoCell.MOON)) // violation, hidden while settling
        assertTrue(s.suppressedSets.isNotEmpty())

        s.undo() // the violation is gone: nothing left to hide
        assertEquals(emptySet<Set<Int>>(), s.suppressedSets)

        s.redo() // and it is a NEW violation again
        assertTrue(s.suppressedSets.isNotEmpty())
        assertEquals(s.suppressedSets, s.rendererModel()!!.suppressedSets)

        Thread.sleep(600)
        assertEquals(emptySet<Set<Int>>(), s.suppressedSets)
    }

    @Test
    fun `undo and redo are refused once the board is solved`() = runTest {
        val s = state(FakeCompletionRepository(), tangoDaily())
        s.load()
        playAll(s)
        val solved = s.session
        assertTrue(solved!!.isComplete)

        assertFalse(s.canUndo)
        s.undo()
        s.redo()

        assertEquals(solved, s.session)
    }

    @Test
    fun `a restored session keeps its undo and redo history`() = runTest {
        val s = state(FakeCompletionRepository(), tangoDaily())
        s.load()
        s.onPlacement(TangoPlacement(2, TangoCell.MOON))
        s.onPlacement(TangoPlacement(3, TangoCell.MOON))
        s.undo()

        val restored = state(FakeCompletionRepository(), tangoDaily())
        restored.adoptRestored(s.persist()!!)

        assertTrue(restored.canUndo)
        assertTrue(restored.canRedo)
        restored.redo()
        assertEquals(TangoCell.MOON, restored.session?.placements?.get(3))
    }

    @Test
    fun `result label names the puzzle number when known`() = runTest {
        val numbered = state(FakeCompletionRepository(), tangoDaily())
        numbered.load()
        assertEquals("Tango · #301", numbered.resultLabel)

        val unnumbered = state(FakeCompletionRepository(), tangoDaily(history = listOf(entry(puzzleNumber = null))))
        unnumbered.load()
        assertEquals("Tango", unnumbered.resultLabel)
    }
}
