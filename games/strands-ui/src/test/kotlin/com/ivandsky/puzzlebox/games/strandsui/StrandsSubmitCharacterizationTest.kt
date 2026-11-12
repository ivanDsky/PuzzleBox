package com.ivandsky.puzzlebox.games.strandsui

import com.ivandsky.puzzlebox.games.strands.ActiveHint
import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strands.HintStage
import com.ivandsky.puzzlebox.games.strands.StrandsSession
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test

/** Pins the controller's session after each kind of submit and hint press, so the reducer extraction cannot shift it. */
class StrandsSubmitCharacterizationTest {

    private fun adopted() = StrandsPlayController(PlayEnvironment(CoroutineScope(Dispatchers.Unconfined), {}, {})).also {
        it.adoptToday(StrandsUiFixtures.envelope(), StrandsUiFixtures.ref)
    }

    private fun StrandsPlayController.submit(path: List<Coord>) = onTrace(TraceStep.Submit(path))

    private fun StrandsPlayController.session(): StrandsSession = checkNotNull(session)

    private fun StrandsPlayController.earnHint() =
        listOf("PIKE", "TAKE", "RAKE").forEach { submit(StrandsUiFixtures.nonTheme.getValue(it)) }

    @Test
    fun `a theme word joins found`() {
        val c = adopted()
        c.submit(StrandsUiFixtures.row(0))

        assertEquals(listOf("PIES"), c.session().state.found)
        assertEquals(emptySet<String>(), c.session().state.nonTheme)
    }

    @Test
    fun `the spangram joins found`() {
        val c = adopted()
        c.submit(StrandsUiFixtures.row(1))

        assertEquals(listOf("BAKE"), c.session().state.found)
    }

    @Test
    fun `a non-theme word joins nonTheme and not found`() {
        val c = adopted()
        c.submit(StrandsUiFixtures.nonTheme.getValue("PIKE"))

        assertEquals(setOf("PIKE"), c.session().state.nonTheme)
        assertEquals(emptyList<String>(), c.session().state.found)
    }

    @Test
    fun `a repeat leaves the session instance alone`() {
        val c = adopted()
        c.submit(StrandsUiFixtures.row(0))
        val before = c.session()
        c.submit(StrandsUiFixtures.row(0))

        assertSame(before, c.session())
    }

    @Test
    fun `an invalid path leaves the session instance alone`() {
        val c = adopted()
        val before = c.session()
        c.submit(listOf(Coord(0, 0), Coord(3, 3), Coord(0, 1), Coord(0, 2)))

        assertSame(before, c.session())
    }

    @Test
    fun `a word missing from the list leaves the session instance alone`() {
        val c = adopted()
        val before = c.session()
        c.submit(listOf(Coord(0, 0), Coord(1, 0), Coord(2, 0), Coord(3, 0)))

        assertSame(before, c.session())
    }

    @Test
    fun `a hint press puts the first stage on the board and counts it`() {
        val c = adopted()
        c.earnHint()
        c.pressHint()

        assertEquals(1, c.session().state.hintsUsed)
        assertEquals(ActiveHint("PIES", HintStage.Outlined), c.session().state.activeHint)
    }

    @Test
    fun `an unearned hint press leaves the session instance alone`() {
        val c = adopted()
        val before = c.session()
        c.pressHint()

        assertSame(before, c.session())
        assertNotNull(c.hintDialog)
    }
}
