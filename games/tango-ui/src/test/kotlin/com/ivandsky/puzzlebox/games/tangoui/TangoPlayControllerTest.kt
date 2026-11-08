package com.ivandsky.puzzlebox.games.tangoui

import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoDefinition
import com.ivandsky.puzzlebox.games.tango.TangoPuzzleRef
import com.ivandsky.puzzlebox.games.tango.codec.TangoSessionCodec
import com.ivandsky.puzzlebox.gameui.Adopt
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.Restored
import com.ivandsky.puzzlebox.gameui.playCaption
import com.ivandsky.puzzlebox.model.PuzzleKey
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Tango play controller tests (tango-05): the game half of the play matrix — the
 * A15 trust gate, placement policy (§6 violations LAND), hint counting, the win
 * transition (ONE onWon per play), restore, undo/redo, and the timer window. Fetch,
 * record write, badge and result label are the play feature's (store-level tests).
 */
class TangoPlayControllerTest {

    private val today: LocalDate = LocalDate.parse("2026-10-31")

    /** Controllable clock: the controller's `now` reads this var. */
    private var fakeNow: Instant = Instant.parse("2026-10-31T00:00:00Z")

    private val saved = mutableListOf<ByteArray>()
    private val won = mutableListOf<CompletionRecord>()

    private fun controller(
        onSessionChanged: (ByteArray) -> Unit = { saved += it },
        onWon: (CompletionRecord) -> Unit = { won += it },
    ) = TangoPlayController(
        env = PlayEnvironment(CoroutineScope(Dispatchers.Unconfined), onSessionChanged, onWon),
        now = { fakeNow },
    )

    /** A controller that adopted today's fixture under ref (today, [puzzleNumber]). */
    private fun loaded(puzzleNumber: Int? = 301): TangoPlayController = controller().also {
        assertEquals(Adopt.Adopted, it.adoptToday(TangoPlayFixture.envelope(), TangoPlayFixture.ref(today, puzzleNumber)))
    }

    /** Places every player-owned cell (the fixture solution) in order. */
    private fun playAll(s: TangoPlayController) {
        for ((idx, value) in TangoPlayFixture.placements) {
            s.onPlacement(TangoPlacement(idx, value))
        }
    }

    @Test
    fun `fresh adopt lands on a ready board with today's ref`() {
        val s = loaded()

        assertEquals(TangoPlayFixture.puzzle, s.session?.puzzle)
        assertEquals(TangoPuzzleRef(PuzzleKey.Daily(today, 301)), s.session?.ref)
        assertEquals(0L, s.session?.elapsedMs)
        assertFalse(s.session?.isComplete ?: true)
        assertFalse(s.isComplete)
        // The initial session round-trips through the callback from load (C1).
        assertEquals(s.session, (TangoSessionCodec.deserialize(saved.single()) as DecodeResult.Ok).value)
    }

    @Test
    fun `corrupt payload is never playable (payload untrusted)`() {
        val s = controller()

        val adopt = s.adoptToday(
            Puzzle(
                gameId = TangoDefinition.id,
                variant = TangoPlayFixture.variant,
                formatVersion = 1,
                payload = ByteArray(7),
            ),
            TangoPlayFixture.ref(today),
        )

        val rejected = adopt as Adopt.Rejected
        assertTrue(rejected.message.contains("didn't load right"))
        assertNull(s.session)
    }

    @Test
    fun `ambiguous puzzle is refused by the A15 gate (A16)`() {
        val s = controller()

        val adopt = s.adoptToday(TangoPlayFixture.envelope(TangoPlayFixture.ambiguous), TangoPlayFixture.ref(today))

        assertTrue((adopt as Adopt.Rejected).message.contains("didn't load right"))
        assertNull(s.session)
    }

    @Test
    fun `valid placements land and correct values complete the session`() {
        val s = loaded()

        val (first, value) = TangoPlayFixture.placements.first()
        s.onPlacement(TangoPlacement(first, value))
        assertEquals(value, s.session?.placements?.get(first))

        playAll(s)

        assertNotNull(s.session?.completedAt)
        assertEquals(0, s.session?.mistakeCount)
        assertTrue(s.isComplete)
    }

    @Test
    fun `a rule-violating placement lands as a mistake and never completes the board`() {
        val s = loaded()

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
        assertNotNull(s.lastRejection)
        assertNull(s.session?.completedAt)
    }

    @Test
    fun `given cells are inert`() {
        val s = loaded()
        val before = s.session

        s.onPlacement(TangoPlacement(0, TangoCell.MOON)) // idx 0 is a SUN given
        assertEquals(before, s.session)
        assertNull(s.lastRejection)
    }

    @Test
    fun `a counted hint auto-places a forced cell and names its reason`() {
        val s = loaded()

        s.requestHint()

        assertEquals(1, s.session?.hintsUsed)
        assertNotNull(s.hintCell)
        assertNotNull(s.hintWhy)
        assertNotNull(s.hintCaption)
    }

    @Test
    fun `a fresh win emits ONE record with the tango ref`() {
        val s = loaded()

        playAll(s)

        assertEquals(1, won.size)
        assertEquals(TangoPuzzleRef(PuzzleKey.Daily(today, 301)), won[0].puzzleRef)
        assertEquals(0, won[0].hints)
    }

    @Test
    fun `a replay emits a second record on its own win`() {
        val s = loaded()
        playAll(s)
        assertEquals(1, won.size)

        s.replay()
        assertFalse(s.isComplete)
        assertEquals(1, won.size)
        playAll(s)

        assertEquals(2, won.size)
        assertEquals(TangoPuzzleRef(PuzzleKey.Daily(today, 301)), won[1].puzzleRef)
    }

    @Test
    fun `persist and restore round-trip through the session codec`() {
        val s = loaded()
        val (first, value) = TangoPlayFixture.placements.first()
        s.onPlacement(TangoPlacement(first, value))

        val restored = controller()
        val result = restored.adoptRestored(s.persist()!!)

        assertEquals(Restored(TangoPuzzleRef(PuzzleKey.Daily(today, 301))), result)
        assertEquals(s.session, restored.session)
        assertEquals(TangoPuzzleRef(PuzzleKey.Daily(today, 301)), restored.session?.ref)
        assertEquals(s.persist()!!.toList(), saved.last().toList())
    }

    @Test
    fun `a restored completed session shows the win without re-emitting`() {
        val s = loaded()
        playAll(s)
        assertEquals(1, won.size)

        val restoredWon = mutableListOf<CompletionRecord>()
        val restored = controller(onWon = { restoredWon += it })
        restored.adoptRestored(s.persist()!!)

        assertTrue(restored.session?.isComplete ?: false)
        assertTrue(restored.isComplete)
        assertTrue(restoredWon.isEmpty())
        assertEquals(1, won.size)
    }

    @Test
    fun `a corrupt restore is unreadable so the feature falls back to a fresh load`() {
        val s = controller()

        assertNull(s.adoptRestored(ByteArray(9)))
        assertNull(s.session)
    }

    /** What the timer bar reads after the loop's next tick. */
    private fun TangoPlayController.shown(): Long {
        tick()
        return displayedElapsedMs()
    }

    @Test
    fun `a fresh puzzle's timer runs with no move made`() {
        val s = loaded()

        fakeNow = fakeNow.plusMillis(4_000)

        assertTrue(s.session!!.placements.isEmpty())
        assertNotNull(s.clockWindow)
        assertEquals(4_000L, s.shown())
    }

    @Test
    fun `a restored in-progress session continues from its saved time and advances`() {
        val first = loaded()
        first.onPlacement(TangoPlacement(TangoPlayFixture.placements.first().first, TangoPlayFixture.placements.first().second))
        fakeNow = fakeNow.plusMillis(3_000)
        first.onBackground()
        val bytes = first.persist()!!

        fakeNow = fakeNow.plusMillis(60_000) // process-death gap: never counted
        val restored = controller()
        assertNotNull(restored.adoptRestored(bytes))
        assertEquals(3_000L, restored.shown())

        fakeNow = fakeNow.plusMillis(2_000)
        assertEquals(5_000L, restored.shown())
    }

    @Test
    fun `a restored untouched session's timer runs too`() {
        val first = loaded()
        first.onBackground()
        val bytes = first.persist()!!

        val restored = controller()
        assertNotNull(restored.adoptRestored(bytes))
        fakeNow = fakeNow.plusMillis(1_500)

        assertEquals(1_500L, restored.shown())
    }

    @Test
    fun `background banks the window and foreground resumes it without a move`() {
        val s = loaded()
        fakeNow = fakeNow.plusMillis(5_000)

        s.onBackground()

        assertEquals(5_000L, s.session?.elapsedMs)
        assertNull(s.clockWindow)
        fakeNow = fakeNow.plusMillis(30_000) // away: never counted
        assertEquals(5_000L, s.shown())

        s.onForeground()
        fakeNow = fakeNow.plusMillis(1_000)

        assertNotNull(s.clockWindow)
        assertEquals(6_000L, s.shown())
    }

    @Test
    fun `completing the board stops the clock`() {
        val s = loaded()
        fakeNow = fakeNow.plusMillis(7_000)

        playAll(s)

        assertTrue(s.isComplete)
        assertNull(s.clockWindow)
        assertEquals(7_000L, s.session?.elapsedMs)
        fakeNow = fakeNow.plusMillis(9_000)
        s.onForeground() // a finished play never reopens
        assertNull(s.clockWindow)
        assertEquals(7_000L, s.shown())
    }

    @Test
    fun `replay restarts from zero and advances at once`() {
        val s = loaded()
        fakeNow = fakeNow.plusMillis(7_000)
        playAll(s)

        s.replay()
        assertEquals(0L, s.shown())
        fakeNow = fakeNow.plusMillis(700)

        assertEquals(700L, s.shown())
    }

    @Test
    fun `the win record carries the active-play time from the open (compete loop, game half)`() {
        val s = loaded()

        fakeNow = fakeNow.plusMillis(90_000) // thinking before the first move counts now
        val (first, firstValue) = TangoPlayFixture.placements.first()
        s.onPlacement(TangoPlacement(first, firstValue))
        fakeNow = fakeNow.plusMillis(45_000)
        for ((idx, value) in TangoPlayFixture.placements.drop(1)) {
            s.onPlacement(TangoPlacement(idx, value)) // completes at T+135s
        }

        assertEquals(1, won.size)
        assertEquals(135_000L, won[0].time)
        assertNotNull(s.session?.completedAt)
    }

    @Test
    fun `violations stay deferred 0_5s after the last touch, then resume`() {
        val s = loaded()
        assertEquals(emptySet<Set<Int>>(), s.suppressedSets)
        assertEquals(emptySet<Set<Int>>(), s.rendererModel()!!.suppressedSets)

        s.onPlacement(TangoPlacement(2, TangoCell.SUN))
        // A single placement that creates a violation: that violation is new → suppressed.
        assertTrue(s.suppressedSets.isNotEmpty())
        assertEquals(s.suppressedSets, s.rendererModel()!!.suppressedSets)

        // The settle job delays on the env's Unconfined scope — real 500 ms.
        Thread.sleep(600)
        assertEquals(emptySet<Set<Int>>(), s.suppressedSets)
        assertEquals(emptySet<Set<Int>>(), s.rendererModel()!!.suppressedSets)
    }

    @Test
    fun `one caption - the settled violation phrase outranks the hint, none is a rejection`() {
        val s = loaded()
        s.requestHint()
        assertNull(s.violation)
        assertEquals(s.hintCaption, playCaption(s, s))

        s.onPlacement(TangoPlacement(2, TangoCell.MOON))
        s.onPlacement(TangoPlacement(3, TangoCell.MOON))
        s.onPlacement(TangoPlacement(4, TangoCell.MOON)) // lands, violating
        assertNull(s.violation) // new violation waits out the settle window
        Thread.sleep(600)
        val phrase = TangoSemantics.firstViolationPhrase(s.rendererModel()!!)
        assertNotNull(phrase)
        assertEquals(phrase, s.violation)
        assertNull(s.rejection) // placements land; Tango never shows a refusal caption
        assertEquals(phrase, playCaption(s, s))
    }

    // ---- undo / redo (command history) ----

    @Test
    fun `undo and redo move the board and persist while counters and clock stay put`() {
        val s = loaded()
        s.onPlacement(TangoPlacement(2, TangoCell.MOON))
        s.onPlacement(TangoPlacement(3, TangoCell.MOON))
        s.onPlacement(TangoPlacement(4, TangoCell.MOON)) // violating: lands, one mistake
        fakeNow = fakeNow.plusMillis(5_000)
        s.onBackground() // banks 5 s and stops the clock
        assertNull(s.clockWindow)
        s.onForeground() // the board is back on screen: the window reopens

        s.undo()

        assertNull(s.session?.placements?.get(4))
        assertEquals(1, s.session?.mistakeCount)
        assertEquals(0, s.session?.hintsUsed)
        assertEquals(5_000L, s.session?.elapsedMs)
        assertNotNull(s.clockWindow) // undo never rewinds the clock
        assertEquals(s.persist()!!.toList(), saved.last().toList())
        assertTrue(s.canRedo)

        s.redo()

        assertEquals(TangoCell.MOON, s.session?.placements?.get(4))
        assertEquals(1, s.session?.mistakeCount)
        assertEquals(5_000L, s.session?.elapsedMs)
        assertEquals(s.persist()!!.toList(), saved.last().toList())
        assertFalse(s.canRedo)
    }

    @Test
    fun `a hint placement is undoable but the hint stays counted`() {
        val s = loaded()
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
    fun `a new placement after an undo drops redo`() {
        val s = loaded()
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
    fun `undoing a violating placement clears the rejection caption`() {
        val s = loaded()
        s.onPlacement(TangoPlacement(2, TangoCell.MOON))
        s.onPlacement(TangoPlacement(3, TangoCell.MOON))
        s.onPlacement(TangoPlacement(4, TangoCell.MOON))
        assertNotNull(s.lastRejection)

        s.undo()

        assertNull(s.lastRejection)
    }

    @Test
    fun `a redo that restores a violation waits out the settle window like a placement`() {
        val s = loaded()
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
    fun `undo and redo are refused once the board is solved`() {
        val s = loaded()
        playAll(s)
        val solved = s.session
        assertTrue(solved!!.isComplete)

        assertFalse(s.canUndo)
        s.undo()
        s.redo()

        assertEquals(solved, s.session)
    }

    @Test
    fun `a restored session keeps its undo and redo history`() {
        val s = loaded()
        s.onPlacement(TangoPlacement(2, TangoCell.MOON))
        s.onPlacement(TangoPlacement(3, TangoCell.MOON))
        s.undo()

        val restored = controller()
        restored.adoptRestored(s.persist()!!)

        assertTrue(restored.canUndo)
        assertTrue(restored.canRedo)
        restored.redo()
        assertEquals(TangoCell.MOON, restored.session?.placements?.get(3))
    }

    // ---- Clear ----

    @Test
    fun `clear wipes the player cells, keeps the givens, persists once and undoes in one step`() {
        val s = loaded()
        s.onPlacement(TangoPlacement(2, TangoCell.MOON))
        s.onPlacement(TangoPlacement(3, TangoCell.MOON))
        s.onPlacement(TangoPlacement(4, TangoCell.MOON)) // violating: lands
        val saves = saved.size

        s.clearBoard()

        assertEquals(emptyMap<Int, TangoCell>(), s.session?.placements)
        assertEquals(TangoPlayFixture.puzzle.givens.size, s.session!!.grid().count { it != null })
        assertEquals(saves + 1, saved.size)
        assertEquals(s.persist()!!.toList(), saved.last().toList())
        assertNull(s.violation) // the wiped violation caption goes with it
        assertEquals(emptySet<Set<Int>>(), s.suppressedSets)

        s.undo()

        assertEquals(mapOf(2 to TangoCell.MOON, 3 to TangoCell.MOON, 4 to TangoCell.MOON), s.session?.placements)
    }

    @Test
    fun `clear on a board with no placements changes and saves nothing`() {
        val s = loaded()
        val before = s.session
        val saves = saved.size

        s.clearBoard()

        assertEquals(before, s.session)
        assertEquals(saves, saved.size)
        assertFalse(s.canUndo)
    }

    @Test
    fun `clear drops the hint reveal and keeps the counted hint`() {
        val s = loaded()
        s.requestHint()
        assertNotNull(s.hintCell)

        s.clearBoard()

        assertNull(s.hintCell)
        assertNull(s.hintCaption)
        assertEquals(1, s.session?.hintsUsed)
    }

    @Test
    fun `clear is refused once the board is solved`() {
        val s = loaded()
        playAll(s)
        val solved = s.session
        val saves = saved.size

        s.clearBoard()

        assertEquals(solved, s.session)
        assertEquals(saves, saved.size)
    }
}
