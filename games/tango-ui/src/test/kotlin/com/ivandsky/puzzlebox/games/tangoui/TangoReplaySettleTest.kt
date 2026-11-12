package com.ivandsky.puzzlebox.games.tangoui

import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoReplay
import com.ivandsky.puzzlebox.games.tango.TangoReplayMove
import com.ivandsky.puzzlebox.games.tango.TangoSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A replay frame settles violations as live play does: new ones wait out the window, old ones show at once. */
class TangoReplaySettleTest {

    private val settle = MotionTokens.SettleMillis
    private val initial = TangoSession(ref = null, puzzle = TangoPlayFixture.puzzle)

    private fun TangoSession.place(idx: Int, value: TangoCell): TangoSession =
        checkNotNull(TangoReplay.step(this, TangoReplayMove.Place(idx, value).toReplayMove(), 1_000L))

    // Givens 0 and 1 are suns: a sun at 2 makes three in a row.
    private val broken = initial.place(2, TangoCell.SUN)
    private val stillBroken = broken.place(8, TangoCell.MOON)

    private fun shown(session: TangoSession, before: TangoSession?, sinceMoveMs: Long, ended: Boolean = false) =
        violatingSets(session.puzzle, session.grid()) - tangoReplaySuppressed(session, before, sinceMoveMs, ended)

    @Test
    fun `fixture breaks a rule`() {
        assertTrue(violatingSets(broken.puzzle, broken.grid()).isNotEmpty())
        assertTrue(violatingSets(initial.puzzle, initial.grid()).isEmpty())
    }

    @Test
    fun `a new violation is hidden before the settle window`() {
        assertTrue(shown(broken, initial, 0L).isEmpty())
        assertTrue(shown(broken, initial, settle - 1).isEmpty())
    }

    @Test
    fun `a new violation shows from the settle window on`() {
        val all = violatingSets(broken.puzzle, broken.grid())
        assertEquals(all, shown(broken, initial, settle))
        assertEquals(all, shown(broken, initial, settle + 1))
    }

    @Test
    fun `a violation already in before shows at once`() {
        val all = violatingSets(stillBroken.puzzle, stillBroken.grid())
        assertTrue(all.containsAll(violatingSets(broken.puzzle, broken.grid())))
        assertTrue(shown(stillBroken, broken, 0L).containsAll(violatingSets(broken.puzzle, broken.grid())))
    }

    @Test
    fun `an ended frame shows everything`() {
        assertEquals(violatingSets(broken.puzzle, broken.grid()), shown(broken, initial, 0L, ended = true))
    }

    @Test
    fun `no move yet hides nothing`() {
        assertTrue(tangoReplaySuppressed(initial, null, 0L, ended = false).isEmpty())
    }
}
