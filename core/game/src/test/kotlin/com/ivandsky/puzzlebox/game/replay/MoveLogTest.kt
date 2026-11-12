package com.ivandsky.puzzlebox.game.replay

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.replay.FakeGameReplay.Companion.add
import com.ivandsky.puzzlebox.game.replay.FakeGameReplay.Companion.assist
import com.ivandsky.puzzlebox.game.replay.FakeGameReplay.Companion.hint
import com.ivandsky.puzzlebox.game.replay.FakeGameReplay.Companion.unknown
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.SolveReplay
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import kotlin.random.Random

class MoveLogTest {

    private val game = GameId("fake")
    private val replay = FakeGameReplay()
    private val start = FakeSession(target = 3)

    @Test
    fun `a move that changes the session is recorded with its stamp`() {
        val log = MoveLog.fresh(game)

        val next = log.play(replay, start, add(2), atMs = 700)

        assertEquals(FakeSession(target = 3, count = 2, movedAtMs = 700), next)
        assertEquals(listOf(StampedMove(700, add(2))), recorded(log).moves)
    }

    @Test
    fun `a move that changes nothing is not recorded`() {
        val log = MoveLog.fresh(game)

        val next = log.play(replay, start, add(0), atMs = 700)

        assertSame(start, next)
        assertEquals(emptyList<StampedMove>(), recorded(log).moves)
    }

    @Test
    fun `a new session equal to the old one counts as changed`() {
        val copying = object : GameReplay<FakeSession> by replay {
            override fun step(session: FakeSession, move: ReplayMove, atMs: Long): FakeSession = session.copy()
        }
        val log = MoveLog.fresh(game)

        log.play(copying, start, add(0), atMs = 700)

        assertEquals(listOf(StampedMove(700, add(0))), recorded(log).moves)
    }

    @Test
    fun `a setting that changes nothing is still recorded`() {
        val log = MoveLog.fresh(game)

        val next = log.setting(replay, start, assist(false), atMs = 0)

        assertSame(start, next)
        assertEquals(listOf(StampedMove(0, assist(false))), recorded(log).moves)
    }

    @Test
    fun `a setting that changes the session returns the new session`() {
        val log = MoveLog.fresh(game)

        val next = log.setting(replay, start, assist(true), atMs = 40)

        assertEquals(FakeSession(target = 3, assist = true), next)
        assertEquals(listOf(StampedMove(40, assist(true))), recorded(log).moves)
    }

    @Test
    fun `a restored log steps the setting it already ends on and records nothing`() {
        val saved = MoveLog.fresh(game)
        val first = saved.setting(replay, start, assist(true), atMs = 0)
        saved.play(replay, first, add(1), atMs = 100)
        val log = MoveLog.restored(game, saved.snapshot())

        // The session as a game saves it: without the setting.
        val next = log.setting(replay, FakeSession(target = 3, count = 1, movedAtMs = 100), assist(true), atMs = 100)

        assertEquals(FakeSession(target = 3, count = 1, assist = true, movedAtMs = 100), next)
        assertArrayEquals(saved.snapshot(), log.snapshot())
    }

    @Test
    fun `a setting that differs from the latest one of its kind is recorded`() {
        val log = MoveLog.fresh(game)
        var session = log.setting(replay, start, assist(true), atMs = 0)
        session = log.play(replay, session, add(1), atMs = 100)

        session = log.setting(replay, session, assist(false), atMs = 200)
        log.setting(replay, session, assist(true), atMs = 300)

        assertEquals(
            listOf(
                StampedMove(0, assist(true)),
                StampedMove(100, add(1)),
                StampedMove(200, assist(false)),
                StampedMove(300, assist(true)),
            ),
            recorded(log).moves,
        )
    }

    @Test
    fun `the first setting of its kind is recorded after other moves`() {
        val log = MoveLog.fresh(game)
        val first = log.play(replay, start, add(1), atMs = 100)

        val next = log.setting(replay, first, assist(false), atMs = 200)

        assertSame(first, next)
        assertEquals(listOf(StampedMove(100, add(1)), StampedMove(200, assist(false))), recorded(log).moves)
    }

    @Test
    fun `a stamp below the previous one is raised to it`() {
        val log = MoveLog.fresh(game)

        val first = log.play(replay, start, add(1), atMs = 900)
        log.play(replay, first, add(1), atMs = 400)

        assertEquals(listOf(StampedMove(900, add(1)), StampedMove(900, add(1))), recorded(log).moves)
    }

    @Test
    fun `the game steps with the raised stamp`() {
        val log = MoveLog.fresh(game)

        val first = log.play(replay, start, add(1), atMs = 900)
        val second = log.play(replay, first, add(1), atMs = 400)

        assertEquals(900, second.movedAtMs)
    }

    @Test
    fun `a stamp below zero is raised to zero`() {
        val log = MoveLog.fresh(game)

        log.play(replay, start, add(1), atMs = -5)

        assertEquals(listOf(StampedMove(0, add(1))), recorded(log).moves)
    }

    @Test
    fun `a move the game does not understand keeps the session and voids the log`() {
        val log = MoveLog.fresh(game)
        val first = log.play(replay, start, add(1), atMs = 10)

        val second = log.play(replay, first, unknown(), atMs = 20)

        assertSame(first, second)
        assertVoid(log)
    }

    @Test
    fun `a setting the game does not understand keeps the session and voids the log`() {
        val log = MoveLog.fresh(game)

        val next = log.setting(replay, start, unknown(), atMs = 0)

        assertSame(start, next)
        assertVoid(log)
    }

    @Test
    fun `an off log and a void log step and record nothing`() {
        val inert = mapOf(
            "off" to MoveLog.off(),
            "restored from nothing" to MoveLog.restored(game, null),
            "voided by an unknown move" to MoveLog.fresh(game).also { it.play(replay, start, unknown(), atMs = 0) },
        )
        for ((name, log) in inert) {
            var session = log.play(replay, start, add(2), atMs = 500)
            session = log.setting(replay, session, assist(true), atMs = 510)
            session = log.play(replay, session, add(1), atMs = 100)

            assertEquals(name, FakeSession(target = 3, count = 3, assist = true, movedAtMs = 100), session)
            assertSame(name, session, log.play(replay, session, unknown(), atMs = 600))
            assertNull(name, log.snapshot())
            assertNull(name, log.seal(ReplayOutcome.Solved, endMs = 600))
        }
    }

    @Test
    fun `restart empties the journal and keeps recording the same game`() {
        val log = MoveLog.fresh(game)
        val session = log.setting(replay, start, assist(true), atMs = 4_500)
        log.play(replay, session, add(1), atMs = 4_600)

        log.restart()

        assertEquals(ReplayRecord(game, ReplayOutcome.Open, endMs = 0, moves = emptyList()), recorded(log))
        log.play(replay, start, add(1), atMs = 500)
        assertEquals(listOf(StampedMove(500, add(1))), recorded(log).moves)
    }

    @Test
    fun `a setting equal to the last move before the restart is recorded again`() {
        val log = MoveLog.fresh(game)
        log.setting(replay, start, assist(true), atMs = 4_500)

        log.restart()
        log.setting(replay, start, assist(true), atMs = 0)

        assertEquals(listOf(StampedMove(0, assist(true))), recorded(log).moves)
    }

    @Test
    fun `restart leaves an off log and a void log as they were`() {
        val inert = listOf(
            MoveLog.off(),
            MoveLog.restored(game, null),
            MoveLog.fresh(game).also { it.play(replay, start, unknown(), atMs = 0) },
        )
        for (log in inert) {
            log.restart()

            assertVoid(log)
        }
    }

    @Test
    fun `a fresh log with no moves snapshots an empty open record`() {
        assertEquals(ReplayRecord(game, ReplayOutcome.Open, endMs = 0, moves = emptyList()), recorded(MoveLog.fresh(game)))
    }

    @Test
    fun `a log with no moves seals to a replay`() {
        val sealed = MoveLog.fresh(game).seal(ReplayOutcome.GaveUp, endMs = 1_000)

        assertEquals(ReplayRecord(game, ReplayOutcome.GaveUp, endMs = 1_000, moves = emptyList()), decoded(sealed))
    }

    @Test
    fun `seal lowers every stamp past the end time to it`() {
        val log = threeMovesAt(100, 250, 400)

        val sealed = log.seal(ReplayOutcome.Solved, endMs = 250)

        val moves = listOf(StampedMove(100, add(1)), StampedMove(250, add(1)), StampedMove(250, add(1)))
        assertEquals(ReplayRecord(game, ReplayOutcome.Solved, endMs = 250, moves = moves), decoded(sealed))
    }

    @Test
    fun `seal leaves the recorded stamps as they were`() {
        val log = threeMovesAt(100, 250, 400)

        log.seal(ReplayOutcome.Solved, endMs = 250)

        assertEquals(listOf(100L, 250L, 400L), recorded(log).moves.map { it.atMs })
    }

    @Test
    fun `an Open outcome seals to nothing and leaves the log untouched`() {
        val log = threeMovesAt(100, 250, 400)

        assertNull(log.seal(ReplayOutcome.Open, endMs = 400))

        assertEquals(listOf(100L, 250L, 400L), recorded(log).moves.map { it.atMs })
    }

    @Test
    fun `4 096 moves are recorded`() {
        val log = MoveLog.fresh(game)

        playAdds(log, count = 4_096) { index -> index.toLong() }

        assertEquals(4_096, recorded(log).moves.size)
        assertEquals(4_096, decoded(log.seal(ReplayOutcome.Solved, endMs = 4_096)).moves.size)
    }

    @Test
    fun `the 4 097th move is played and voids the log`() {
        val log = MoveLog.fresh(game)

        val last = playAdds(log, count = 4_097) { index -> index.toLong() }

        assertEquals(4_097, last.count)
        assertVoid(log)
    }

    @Test
    fun `the log is void from the 4 097th move on, not from the next snapshot`() {
        val log = MoveLog.fresh(game)
        val last = playAdds(log, count = 4_097) { 5_000 }

        val next = log.play(replay, last, add(1), atMs = 20)

        // Only a recording log raises a stamp.
        assertEquals(20, next.movedAtMs)
    }

    @Test
    fun `a log the codec refused to snapshot stays void`() {
        // Ten layout bytes a move pass the codec's 32 KiB layout cap. Lowered to an end time of 0
        // they would be three bytes a move, which the codec takes: only a void log still refuses.
        val log = MoveLog.fresh(game)
        playAdds(log, count = 4_096) { index -> (index + 1L) shl 49 }

        assertNull(log.snapshot())
        assertNull(log.seal(ReplayOutcome.Solved, endMs = 0))
    }

    @Test
    fun `a replay over 12 KiB seals to nothing`() {
        val log = incompressibleLog()

        assertNotNull(log.snapshot())
        assertNull(log.seal(ReplayOutcome.Solved, endMs = 4_096))
    }

    @Test
    fun `a log refused at seal stays void`() {
        val log = incompressibleLog()

        log.seal(ReplayOutcome.Solved, endMs = 4_096)

        assertVoid(log)
    }

    @Test
    fun `a restored log seals to the bytes of an uninterrupted one`() {
        val uninterrupted = MoveLog.fresh(game)
        playSecondHalf(uninterrupted, playFirstHalf(uninterrupted))

        val saved = MoveLog.fresh(game)
        val savedSession = playFirstHalf(saved)
        val resumed = MoveLog.restored(game, saved.snapshot())
        playSecondHalf(resumed, savedSession)

        val sealed = resumed.seal(ReplayOutcome.Solved, endMs = 8_000)
        val moves = listOf(
            StampedMove(0, assist(false)),
            StampedMove(1_200, add(1)),
            StampedMove(2_050, hint()),
            StampedMove(2_050, assist(true)),
            StampedMove(8_000, add(1)),
        )
        assertEquals(ReplayRecord(game, ReplayOutcome.Solved, endMs = 8_000, moves = moves), decoded(sealed))
        assertArrayEquals(uninterrupted.seal(ReplayOutcome.Solved, endMs = 8_000)?.toByteArray(), sealed?.toByteArray())
    }

    @Test
    fun `a snapshot with no moves restores to a recording log`() {
        val resumed = MoveLog.restored(game, MoveLog.fresh(game).snapshot())

        resumed.play(replay, start, add(1), atMs = 30)

        assertEquals(listOf(StampedMove(30, add(1))), recorded(resumed).moves)
    }

    @Test
    fun `a snapshot of another game restores to a void log`() {
        val snapshot = threeMovesAt(100, 250, 400).snapshot()

        assertVoid(MoveLog.restored(GameId("other"), snapshot))
    }

    @Test
    fun `a sealed replay restores to a void log`() {
        val sealed = checkNotNull(threeMovesAt(100, 250, 400).seal(ReplayOutcome.Solved, endMs = 400))

        assertVoid(MoveLog.restored(game, sealed.toByteArray()))
    }

    @Test
    fun `unreadable bytes restore to a void log`() {
        assertVoid(MoveLog.restored(game, byteArrayOf(9, 9, 9)))
    }

    private fun playFirstHalf(log: MoveLog): FakeSession {
        var session = log.setting(replay, start, assist(false), atMs = 0)
        session = log.play(replay, session, add(1), atMs = 1_200)
        return log.play(replay, session, hint(), atMs = 2_050)
    }

    /** Its setting is stamped below the last move of the first half, and its last move past the end time. */
    private fun playSecondHalf(log: MoveLog, from: FakeSession): FakeSession {
        var session = log.play(replay, from, add(0), atMs = 2_500)
        session = log.setting(replay, session, assist(true), atMs = 1_900)
        return log.play(replay, session, add(1), atMs = 9_000)
    }

    private fun threeMovesAt(first: Long, second: Long, third: Long): MoveLog {
        val log = MoveLog.fresh(game)
        var session = start
        for (atMs in longArrayOf(first, second, third)) session = log.play(replay, session, add(1), atMs)
        return log
    }

    private fun playAdds(log: MoveLog, count: Int, by: () -> Int = { 1 }, atMs: (Int) -> Long): FakeSession {
        var session = start
        repeat(count) { index -> session = log.play(replay, session, add(by()), atMs(index)) }
        return session
    }

    /** 4 096 random positive ints carry over 15 KiB of entropy, more than deflate can bring under the 12 KiB cap. */
    private fun incompressibleLog(): MoveLog {
        val random = Random(12)
        val log = MoveLog.fresh(game)
        playAdds(log, count = 4_096, by = { random.nextInt(1, Int.MAX_VALUE) }) { index -> index.toLong() }
        return log
    }

    private fun recorded(log: MoveLog): ReplayRecord = decoded(checkNotNull(log.snapshot()) { "the log is void" })

    private fun decoded(sealed: SolveReplay?): ReplayRecord = decoded(checkNotNull(sealed) { "no replay" }.toByteArray())

    private fun decoded(bytes: ByteArray): ReplayRecord = (ReplayCodec.decode(bytes) as DecodeResult.Ok).value

    private fun assertVoid(log: MoveLog) {
        assertNull(log.snapshot())
        assertNull(log.seal(ReplayOutcome.Solved, endMs = 1_000))
    }
}
