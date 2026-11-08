package com.ivandsky.puzzlebox.games.strandsui

import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strands.HintStage
import com.ivandsky.puzzlebox.games.strands.StrandsSession
import com.ivandsky.puzzlebox.games.strands.codec.StrandsSessionCodec
import com.ivandsky.puzzlebox.games.strands.render.CellState
import com.ivandsky.puzzlebox.gameui.Adopt
import com.ivandsky.puzzlebox.gameui.BROKEN_PUZZLE_MESSAGE
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.Restored
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Strands session holder against the fixture board: submit → verdict + persist, a
 * solve emits exactly one record, a mid-play save restores, one hint press spends exactly
 * one hint, and the clock runs on the injected time.
 */
class StrandsPlayControllerTest {

    private val saved = mutableListOf<ByteArray>()
    private val won = mutableListOf<CompletionRecord>()
    private var clockMs = 0L

    private fun controller() = StrandsPlayController(
        env = PlayEnvironment(CoroutineScope(Dispatchers.Unconfined), { saved += it }, { won += it }),
        now = { Instant.ofEpochMilli(clockMs) },
    )

    private fun adopted() = controller().also {
        assertEquals(Adopt.Adopted, it.adoptToday(StrandsUiFixtures.envelope(), StrandsUiFixtures.ref))
    }

    /** Taps [path] letter by letter, then its last letter again to submit. */
    private fun StrandsPlayController.play(path: List<Coord>) {
        path.forEach(::tap)
        tap(path.last())
    }

    private fun StrandsPlayController.solve() = (0 until 4).forEach { play(StrandsUiFixtures.row(it)) }

    private fun lastSaved(): StrandsSession =
        (StrandsSessionCodec.deserialize(saved.last()) as DecodeResult.Ok).value

    @Test
    fun `adopting today's puzzle persists it and shows its header`() {
        val c = adopted()

        assertEquals(1, saved.size)
        assertEquals(StrandsUiFixtures.ref, lastSaved().ref)
        assertEquals(StrandsHeader("Bake sale", (StrandsUiFixtures.ref.key as PuzzleKey.Daily).puzzleNumber, "by Tess Tester"), c.header)
        assertEquals(4, c.model.rows)
        assertEquals(4, c.model.totalCount)
        assertEquals(WordSlot.Empty, c.slot)
    }

    @Test
    fun `a foreign or broken envelope is rejected`() {
        val good = StrandsUiFixtures.envelope()
        val foreign = Puzzle(GameId("zip"), good.variant, good.formatVersion, good.payload)
        assertEquals(Adopt.Rejected(BROKEN_PUZZLE_MESSAGE), controller().adoptToday(foreign, StrandsUiFixtures.ref))
        val broken = Puzzle(good.gameId, good.variant, good.formatVersion, byteArrayOf(9, 9))
        assertEquals(Adopt.Rejected(BROKEN_PUZZLE_MESSAGE), controller().adoptToday(broken, StrandsUiFixtures.ref))
    }

    @Test
    fun `tapping letters traces a live word that is not persisted`() {
        val c = adopted()
        c.tap(Coord(0, 0))
        c.tap(Coord(0, 1))

        assertEquals(WordSlot.Tracing("PI"), c.slot)
        assertEquals(listOf(0, 1), c.model.cells.take(2).map { it.traceIndex })
        assertEquals(1, saved.size)

        // An earlier letter truncates to it; the lone tail tapped again clears.
        c.tap(Coord(0, 0))
        assertEquals(WordSlot.Tracing("P"), c.slot)
        c.tap(Coord(0, 0))
        assertEquals(WordSlot.Empty, c.slot)
    }

    @Test
    fun `submitting a theme word paints it, reports it and persists it`() {
        val c = adopted()
        c.play(StrandsUiFixtures.row(0))

        assertEquals(WordSlot.Verdict("PIES", VerdictKind.Theme, 1), c.slot)
        assertEquals(FoundEvent(StrandsUiFixtures.row(0), spangram = false, seq = 1), c.lastFound)
        assertEquals(1, c.model.foundCount)
        assertTrue(c.model.cells.take(4).all { it.state == CellState.FoundTheme && it.traceIndex == null })
        assertEquals(listOf("PIES"), lastSaved().state.found)
    }

    @Test
    fun `the spangram is announced and flagged`() {
        val c = adopted()
        c.play(StrandsUiFixtures.row(1))

        assertEquals(WordSlot.Verdict("Spangram!", VerdictKind.Spangram, 1), c.slot)
        assertEquals(true, c.lastFound?.spangram)
    }

    @Test
    fun `a found word is handed over once`() {
        val c = adopted()
        c.play(StrandsUiFixtures.row(0))

        assertEquals(FoundEvent(StrandsUiFixtures.row(0), spangram = false, seq = 1), c.takeFound())
        assertNull(c.takeFound())
        assertNull(c.lastFound)
    }

    @Test
    fun `a rejected word changes nothing and is not persisted, but each verdict bumps seq`() {
        val c = adopted()
        c.play(listOf(Coord(0, 0), Coord(0, 1), Coord(0, 2)))
        assertEquals(WordSlot.Verdict("Too short", VerdictKind.Reject, 1), c.slot)
        c.play(listOf(Coord(0, 0), Coord(0, 1), Coord(0, 2)))
        assertEquals(WordSlot.Verdict("Too short", VerdictKind.Reject, 2), c.slot)
        assertEquals(1, saved.size)
        assertNull(c.lastFound)
    }

    @Test
    fun `a non-theme word shows itself and fills the hint meter`() {
        val c = adopted()
        c.play(StrandsUiFixtures.nonTheme.getValue("PIKE"))

        assertEquals(WordSlot.Verdict("PIKE", VerdictKind.NonTheme, 1), c.slot)
        assertEquals(HintUi(available = false, meterThirds = 1), c.hint)
        assertEquals(setOf("PIKE"), lastSaved().state.nonTheme)
    }

    @Test
    fun `solving emits exactly one record and freezes the board`() {
        val c = adopted()
        c.solve()

        assertTrue(c.isComplete)
        assertEquals(1, won.size)
        assertEquals(StrandsUiFixtures.ref, won.single().puzzleRef)
        assertEquals(0, c.solvedHints)

        val saves = saved.size
        c.play(StrandsUiFixtures.row(0))
        assertEquals(saves, saved.size)
        assertEquals(1, won.size)
    }

    @Test
    fun `a mid-play save restores the found words, and a finished one never wins again`() {
        val first = adopted()
        first.play(StrandsUiFixtures.row(0))
        first.play(StrandsUiFixtures.nonTheme.getValue("PIKE"))

        val restored = controller()
        assertEquals(Restored(StrandsUiFixtures.ref), restored.adoptRestored(saved.last()))
        assertEquals(1, restored.model.foundCount)
        assertEquals(1, restored.hint.meterThirds)
        assertEquals(WordSlot.Empty, restored.slot)
        assertNull(restored.solvedHints)

        (1 until 4).forEach { restored.play(StrandsUiFixtures.row(it)) }
        assertEquals(1, won.size)
        val again = controller()
        again.adoptRestored(saved.last())
        assertTrue(again.isComplete)
        assertEquals(1, won.size)
    }

    @Test
    fun `an unreadable save restores nothing`() {
        assertNull(controller().adoptRestored(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun `one hint press spends exactly one hint, and an unearned press explains instead`() {
        val c = adopted()
        c.pressHint()
        assertEquals(HintDialog.NoneAvailable, c.hintDialog)
        c.dismissHintDialog()
        assertNull(c.hintDialog)

        listOf("PIKE", "TAKE", "RAKE").forEach { c.play(StrandsUiFixtures.nonTheme.getValue(it)) }
        assertEquals(HintUi(available = true, meterThirds = 0), c.hint)

        c.pressHint()
        assertEquals(1, lastSaved().state.hintsUsed)
        assertEquals(HintStage.Outlined, lastSaved().state.activeHint?.stage)
        assertNull(c.hintDialog)

        c.pressHint()
        assertEquals(1, lastSaved().state.hintsUsed)
        assertEquals(HintDialog.NoneAvailable, c.hintDialog)
    }

    @Test
    fun `a fully shown hint says it is on the board`() {
        val c = adopted()
        StrandsUiFixtures.nonTheme.values.forEach { c.play(it) }
        c.pressHint()
        c.pressHint()
        assertEquals(2, lastSaved().state.hintsUsed)
        assertEquals(HintStage.OrderRevealed, lastSaved().state.activeHint?.stage)

        c.pressHint()
        assertEquals(HintDialog.OnBoard, c.hintDialog)
        assertEquals(2, lastSaved().state.hintsUsed)
    }

    /** What the timer bar reads after the loop's next tick. */
    private fun StrandsPlayController.shown(): Long {
        tick()
        return displayedElapsedMs()
    }

    @Test
    fun `a fresh puzzle's timer runs with no word found`() {
        clockMs = 1_000
        val c = adopted()
        assertNotNull(c.clockWindow)

        clockMs = 5_000

        assertEquals(0, c.model.foundCount)
        assertEquals(4_000, c.shown())
    }

    @Test
    fun `a restored in-progress session continues from its saved time and advances`() {
        clockMs = 1_000
        val first = adopted()
        first.play(StrandsUiFixtures.row(0))
        clockMs = 4_000
        first.onBackground()

        clockMs = 90_000 // process-death gap: never counted
        val c = controller()
        assertNotNull(c.adoptRestored(saved.last()))
        assertEquals(3_000, c.shown())
        clockMs = 92_000
        assertEquals(5_000, c.shown())
    }

    @Test
    fun `a restored untouched session's timer runs too`() {
        clockMs = 1_000
        val first = adopted()
        first.onBackground()

        clockMs = 50_000
        val c = controller()
        assertNotNull(c.adoptRestored(saved.last()))
        clockMs = 51_500

        assertEquals(1_500, c.shown())
    }

    @Test
    fun `the clock banks on background, resumes without a word, and banks on the win`() {
        clockMs = 1_000
        val c = adopted()

        clockMs = 2_000
        c.play(StrandsUiFixtures.row(0))
        clockMs = 6_000
        assertEquals(5_000, c.shown())

        c.onBackground()
        assertEquals(5_000, lastSaved().elapsedMs)
        assertNull(c.clockWindow)
        clockMs = 8_000
        assertEquals(5_000, c.shown())

        clockMs = 10_000
        c.onForeground()
        assertNotNull(c.clockWindow)
        clockMs = 11_000
        assertEquals(6_000, c.shown())
        (1 until 4).forEach { c.play(StrandsUiFixtures.row(it)) }
        assertNull(c.clockWindow)
        assertEquals(6_000, lastSaved().elapsedMs)
        assertEquals(6_000, won.single().time)
        c.onForeground()
        assertNull("a finished session never restarts it", c.clockWindow)
    }

    @Test
    fun `replay starts the same puzzle over and its win is a new transition`() {
        val c = adopted()
        c.solve()
        c.replay()

        assertFalse(c.isComplete)
        assertEquals(0, c.model.foundCount)
        assertEquals(0L, lastSaved().elapsedMs)
        c.solve()
        assertEquals(2, won.size)
    }

    @Test
    fun `replay restarts the clock from zero and advances at once`() {
        clockMs = 1_000
        val c = adopted()
        clockMs = 9_000
        c.solve()
        assertNull(c.clockWindow)

        c.replay()
        assertNotNull(c.clockWindow)
        assertEquals(0L, c.shown())
        clockMs = 9_700

        assertEquals(700L, c.shown())
    }
}
