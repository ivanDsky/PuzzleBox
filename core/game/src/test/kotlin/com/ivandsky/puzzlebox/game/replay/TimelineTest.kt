package com.ivandsky.puzzlebox.game.replay

import com.ivandsky.puzzlebox.game.replay.CountingReplayGame.Companion.idle
import com.ivandsky.puzzlebox.game.replay.CountingReplayGame.Companion.mark
import com.ivandsky.puzzlebox.game.replay.CountingReplayGame.Companion.unknown
import com.ivandsky.puzzlebox.game.replay.CountingReplayGame.Session
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.SolveReplay
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.random.Random

class TimelineTest {

    private val marks = GameId("marks")

    /** Four marks solve it. The move at 250 changes nothing; 400 carries two moves. */
    private val solve = listOf(
        StampedMove(100, mark(3)),
        StampedMove(250, idle),
        StampedMove(400, mark(1)),
        StampedMove(400, mark(4)),
        StampedMove(900, mark(1)),
    )
    private val afterFirst = Session(marks = 1, trail = 3, markedAtMs = 100)
    private val afterSecond = Session(marks = 2, trail = 94, markedAtMs = 400)
    private val afterThird = Session(marks = 3, trail = 2_918, markedAtMs = 400)
    private val afterLast = Session(marks = 4, trail = 90_459, markedAtMs = 900)

    private val timeline = opened(solve, ReplayOutcome.Solved, endMs = 1_000)

    @Test
    fun `an opened timeline carries the game, the end time and the outcome`() {
        assertEquals(marks, timeline.game)
        assertEquals(1_000L, timeline.endMs)
        assertEquals(ReplayOutcome.Solved, timeline.outcome)
    }

    @Test
    fun `the frame at every stamp equals a straight fold`() {
        val straight = StraightFold(solve, endMs = 1_000, goal = 4)
        for (stamped in solve) straight.assertFrame(timeline.at(stamped.atMs), stamped.atMs)
    }

    @Test
    fun `the frame at a stamp is the board after the move stamped there`() =
        assertFrame(timeline.at(100), session = afterFirst, before = Session(), atMs = 100, sinceMoveMs = 0)

    @Test
    fun `between stamps the board is the one after the last move before t`() =
        assertFrame(timeline.at(399), session = afterFirst, before = Session(), atMs = 399, sinceMoveMs = 299)

    @Test
    fun `before the first move the board is the initial session`() =
        assertFrame(timeline.at(99), session = Session(), before = null, atMs = 99, sinceMoveMs = 99)

    @Test
    fun `t below zero clamps to zero`() =
        assertFrame(timeline.at(-5), session = Session(), before = null, atMs = 0, sinceMoveMs = 0)

    @Test
    fun `the lowest t clamps to zero`() =
        assertFrame(timeline.at(Long.MIN_VALUE), session = Session(), before = null, atMs = 0, sinceMoveMs = 0)

    @Test
    fun `t at endMs is the final board, ended`() =
        assertFrame(timeline.at(1_000), session = afterLast, before = afterThird, atMs = 1_000, sinceMoveMs = 100, ended = true)

    @Test
    fun `t above endMs clamps to endMs`() =
        assertFrame(timeline.at(60_000), session = afterLast, before = afterThird, atMs = 1_000, sinceMoveMs = 100, ended = true)

    @Test
    fun `t just below endMs is not ended`() =
        assertFrame(timeline.at(999), session = afterLast, before = afterThird, atMs = 999, sinceMoveMs = 99)

    @Test
    fun `equal stamps are one tick`() {
        assertEquals(1, timeline.moveTimesMs.count { it == 400L })
        assertFrame(timeline.at(400), session = afterThird, before = afterFirst, atMs = 400, sinceMoveMs = 0)
    }

    @Test
    fun `a move that changes nothing is not a tick`() {
        assertArrayEquals(longArrayOf(100, 400, 900), timeline.moveTimesMs)
        assertFrame(timeline.at(250), session = afterFirst, before = Session(), atMs = 250, sinceMoveMs = 150)
    }

    @Test
    fun `a gave-up replay freezes on its last board at its end time`() {
        val gaveUp = opened(solve.take(3), ReplayOutcome.GaveUp, endMs = 700)
        assertEquals(ReplayOutcome.GaveUp, gaveUp.outcome)
        assertFrame(gaveUp.at(5_000), session = afterSecond, before = afterFirst, atMs = 700, sinceMoveMs = 300, ended = true)
    }

    @Test
    fun `a replay with no moves stays on the initial session`() {
        val untouched = opened(emptyList(), ReplayOutcome.GaveUp, endMs = 700)
        assertEquals(0, untouched.moveTimesMs.size)
        assertFrame(untouched.at(700), session = Session(), before = null, atMs = 700, sinceMoveMs = 700, ended = true)
    }

    @Test
    fun `a move the game does not understand gives no replay`() {
        val moves = listOf(StampedMove(100, mark(3)), StampedMove(200, unknown), StampedMove(300, mark(1)))
        assertNull(open(sealed(moves, ReplayOutcome.GaveUp, endMs = 1_000)))
    }

    @Test
    fun `a solved replay that does not fold to a solved board gives no replay`() =
        assertNull(open(sealed(solve.dropLast(1), ReplayOutcome.Solved, endMs = 1_000)))

    @Test
    fun `the same unsolved fold opens when the player gave up`() =
        assertNotNull(open(sealed(solve.dropLast(1), ReplayOutcome.GaveUp, endMs = 1_000)))

    @Test
    fun `bytes of another game give no replay`() =
        assertNull(open(sealed(solve, ReplayOutcome.Solved, endMs = 1_000, game = GameId("other"))))

    @Test
    fun `an Open outcome gives no replay`() =
        assertNull(open(sealed(solve, ReplayOutcome.Open, endMs = 1_000)))

    @Test
    fun `unreadable bytes give no replay`() =
        assertNull(open(checkNotNull(SolveReplay.of(byteArrayOf(9, 9, 9)))))

    @Test
    fun `factsOf is called exactly once per open`() {
        val game = CountingReplayGame(goal = 4)
        val counted = checkNotNull(open(sealed(solve, ReplayOutcome.Solved, endMs = 1_000), game))
        repeat(60) { counted.at(it * 20L) }
        assertEquals(1, game.factsOfCalls)
    }

    @Test
    fun `a 4 096-move timeline answers 1 000 random seeks with at most 64 steps each`() {
        val random = Random(23)
        var atMs = 0L
        val moves = List(ReplayCodec.MAX_MOVES) {
            atMs += random.nextInt(40)
            StampedMove(atMs, mark(random.nextInt(9)))
        }
        val endMs = atMs + 500
        val game = CountingReplayGame(goal = ReplayCodec.MAX_MOVES)
        val long = checkNotNull(open(sealed(moves, ReplayOutcome.Solved, endMs), game))
        val straight = StraightFold(moves, endMs, goal = ReplayCodec.MAX_MOVES)
        repeat(1_000) {
            val tMs = random.nextLong(-100, endMs + 100)
            val stepsBefore = game.stepCalls
            val frame = long.at(tMs)
            val steps = game.stepCalls - stepsBefore
            assertTrue("$steps steps for t=$tMs", steps <= 64)
            straight.assertFrame(frame, tMs)
        }
    }

    @Test
    fun `forward play costs one step per move`() {
        val game = CountingReplayGame(goal = 200)
        val played = checkNotNull(open(sealed(everyTenMs(200), ReplayOutcome.Solved, endMs = 2_500), game))
        for (move in 1..200) {
            val stepsBefore = game.stepCalls
            played.at(move * 10L)
            assertEquals("move $move", 1, game.stepCalls - stepsBefore)
        }
    }

    @Test
    fun `a frame between the same two moves costs no step`() {
        val game = CountingReplayGame(goal = 200)
        val played = checkNotNull(open(sealed(everyTenMs(200), ReplayOutcome.Solved, endMs = 2_500), game))
        played.at(1_503)
        val stepsBefore = game.stepCalls
        val frame = played.at(1_507)
        assertEquals(0, game.stepCalls - stepsBefore)
        StraightFold(everyTenMs(200), endMs = 2_500, goal = 200).assertFrame(frame, 1_507)
    }

    @Test
    fun `a jump ahead past a checkpoint starts from that checkpoint`() {
        val game = CountingReplayGame(goal = 200)
        val played = checkNotNull(open(sealed(everyTenMs(200), ReplayOutcome.Solved, endMs = 2_500), game))
        played.at(100)
        val stepsBefore = game.stepCalls
        val frame = played.at(1_000)
        assertEquals(36, game.stepCalls - stepsBefore)
        StraightFold(everyTenMs(200), endMs = 2_500, goal = 200).assertFrame(frame, 1_000)
    }

    @Test
    fun `stamps above endMs land on endMs`() {
        val moves = listOf(StampedMove(50, mark(1)), StampedMove(150, mark(2)), StampedMove(Long.MAX_VALUE, mark(3)))
        val late = checkNotNull(open(sealed(moves, ReplayOutcome.Solved, endMs = 100), CountingReplayGame(goal = 3)))
        assertArrayEquals(longArrayOf(50, 100), late.moveTimesMs)
        assertFrame(late.at(99), session = Session(1, 1, 50), before = Session(), atMs = 99, sinceMoveMs = 49, goal = 3)
        val last = Session(marks = 3, trail = 1_026, markedAtMs = 100)
        val beforeLast = Session(marks = 1, trail = 1, markedAtMs = 50)
        assertFrame(late.at(100), session = last, before = beforeLast, atMs = 100, sinceMoveMs = 0, ended = true, goal = 3)
        assertFrame(late.at(Long.MAX_VALUE), session = last, before = beforeLast, atMs = 100, sinceMoveMs = 0, ended = true, goal = 3)
    }

    @Test
    fun `stamps up to the highest Long do not overflow`() {
        val top = Long.MAX_VALUE
        val moves = listOf(StampedMove(7, mark(1)), StampedMove(top - 1, mark(2)), StampedMove(top, mark(3)))
        val far = checkNotNull(open(sealed(moves, ReplayOutcome.Solved, endMs = top), CountingReplayGame(goal = 3)))
        val first = Session(marks = 1, trail = 1, markedAtMs = 7)
        val second = Session(marks = 2, trail = 33, markedAtMs = top - 1)
        val third = Session(marks = 3, trail = 1_026, markedAtMs = top)
        assertArrayEquals(longArrayOf(7, top - 1, top), far.moveTimesMs)
        assertFrame(far.at(Long.MIN_VALUE), session = Session(), before = null, atMs = 0, sinceMoveMs = 0, goal = 3)
        assertFrame(far.at(top - 2), session = first, before = Session(), atMs = top - 2, sinceMoveMs = top - 9, goal = 3)
        assertFrame(far.at(top - 1), session = second, before = first, atMs = top - 1, sinceMoveMs = 0, goal = 3)
        assertFrame(far.at(top), session = third, before = second, atMs = top, sinceMoveMs = 0, ended = true, goal = 3)
    }

    @Test
    fun `threads sharing one timeline all read straight-fold frames`() {
        val moves = everyTenMs(1_000)
        val shared = checkNotNull(open(sealed(moves, ReplayOutcome.Solved, endMs = 10_500), CountingReplayGame(goal = 1_000)))
        val straight = StraightFold(moves, endMs = 10_500, goal = 1_000)
        val pool = Executors.newFixedThreadPool(4)
        try {
            val seekers = List(4) { seed ->
                pool.submit(
                    Callable {
                        val random = Random(seed)
                        repeat(5_000) {
                            val tMs = random.nextLong(0, 10_600)
                            straight.assertFrame(shared.at(tMs), tMs)
                        }
                    },
                )
            }
            seekers.forEach { it.get() }
        } finally {
            pool.shutdownNow()
        }
    }

    /** A mark of 9 is a Mistake, of 8 a Hint; [transient] answers the hook, and it sees the three sessions it is given. */
    private class MistakeReplay(private val transient: Boolean) : GameReplay<Session> {
        private val inner = CountingReplayGame(goal = 4)
        var lastHookCall: Triple<Session, Session, Session>? = null

        override fun step(session: Session, move: ReplayMove, atMs: Long) = inner.step(session, move, atMs)

        override fun factsOf(initial: Session) = inner.factsOf(initial)

        override fun markOf(before: Session, move: ReplayMove, after: Session, previous: ReplayMove?) = when (move.args.firstOrNull()) {
            9 -> MoveMark(MoveKind.Mistake, emptySet())
            8 -> MoveMark(MoveKind.Hint, emptySet())
            else -> MoveMark.Plain
        }

        override fun isTransientMistake(before: Session, after: Session, next: Session): Boolean {
            lastHookCall = Triple(before, after, next)
            return transient
        }
    }

    private fun kindsOf(transient: Boolean, vararg moves: StampedMove): Array<MoveKind> =
        checkNotNull(Timeline.open(marks, MistakeReplay(transient), Session(), sealed(moves.toList(), ReplayOutcome.GaveUp, endMs = 9_000)))
            .moveKindsAt

    @Test
    fun `a transient Mistake the next group settles within 500 ms leaves the timeline, any other stays`() {
        val normal = MoveKind.Normal
        val mistake = MoveKind.Mistake
        // Downgraded: the next group lands 499 ms later and the game says transient.
        assertArrayEquals(
            arrayOf(normal, normal, normal, normal),
            kindsOf(true, StampedMove(100, mark(1)), StampedMove(1_000, mark(9)), StampedMove(1_499, mark(2)), StampedMove(3_000, mark(3))),
        )
        // Downgraded to the highest other kind of its own group.
        assertArrayEquals(
            arrayOf(MoveKind.Hint, normal),
            kindsOf(true, StampedMove(1_000, mark(9)), StampedMove(1_000, mark(8)), StampedMove(1_100, mark(2))),
        )
        // Stays: the next group lands at 500 ms, the game says lasting, or no group follows.
        assertArrayEquals(arrayOf(mistake, normal), kindsOf(true, StampedMove(1_000, mark(9)), StampedMove(1_500, mark(2))))
        assertArrayEquals(arrayOf(mistake, normal), kindsOf(false, StampedMove(1_000, mark(9)), StampedMove(1_100, mark(2))))
        assertArrayEquals(arrayOf(normal, mistake), kindsOf(true, StampedMove(1_000, mark(1)), StampedMove(1_100, mark(9))))
        // The hook sees the board before the Mistake group, after it, and after the whole next group.
        val replay = MistakeReplay(transient = true)
        val moves = listOf(StampedMove(100, mark(1)), StampedMove(1_000, mark(9)), StampedMove(1_200, mark(2)), StampedMove(1_200, mark(3)))
        checkNotNull(Timeline.open(marks, replay, Session(), sealed(moves, ReplayOutcome.GaveUp, endMs = 9_000)))
        val (before, after, next) = checkNotNull(replay.lastHookCall)
        assertEquals(listOf(1, 2, 4), listOf(before.marks, after.marks, next.marks))
    }

    private fun everyTenMs(count: Int) = List(count) { index -> StampedMove((index + 1) * 10L, mark(index % 9)) }

    private fun sealed(moves: List<StampedMove>, outcome: ReplayOutcome, endMs: Long, game: GameId = marks): SolveReplay {
        val stored = checkNotNull(ReplayCodec.encode(ReplayRecord(game, outcome, endMs, moves))) { "encode returned null" }
        return checkNotNull(SolveReplay.of(stored)) { "sealed at ${stored.size} bytes" }
    }

    private fun open(bytes: SolveReplay, game: CountingReplayGame = CountingReplayGame(goal = 4)): Timeline? =
        Timeline.open(marks, game, Session(), bytes)

    private fun opened(moves: List<StampedMove>, outcome: ReplayOutcome, endMs: Long): Timeline =
        checkNotNull(open(sealed(moves, outcome, endMs))) { "open returned null" }

    private fun assertFrame(
        frame: ReplayFrame,
        session: Session,
        before: Session?,
        atMs: Long,
        sinceMoveMs: Long,
        ended: Boolean = false,
        goal: Int = 4,
    ) {
        assertEquals(session, frame.session)
        assertEquals(before, frame.before)
        assertEquals(ReplayFacts(hints = 0, correct = session.marks, total = goal, solved = session.marks == goal), frame.facts)
        assertEquals(atMs, frame.atMs)
        assertEquals(sinceMoveMs, frame.sinceMoveMs)
        assertEquals(ended, frame.ended)
    }
}

/** The oracle: one plain fold kept whole, with no checkpoints. For records whose stamps stay within [endMs]. */
private class StraightFold(moves: List<StampedMove>, private val endMs: Long, goal: Int) {

    private val game = CountingReplayGame(goal)
    private val facts = game.factsOf(Session())
    private val stamps = ArrayList<Long>()
    private val sessions = arrayListOf(Session())

    init {
        for ((atMs, move) in moves) {
            val next = checkNotNull(game.step(sessions.last(), move, atMs))
            if (next !== sessions.last()) {
                stamps += atMs
                sessions += next
            }
        }
    }

    fun assertFrame(frame: ReplayFrame, tMs: Long) {
        val clampedMs = tMs.coerceIn(0, endMs)
        val applied = stamps.count { it <= clampedMs }
        assertEquals("session at $tMs", sessions[applied], frame.session)
        assertEquals("before at $tMs", if (applied == 0) null else sessions[stamps.indexOf(stamps[applied - 1])], frame.before)
        assertEquals("facts at $tMs", facts(sessions[applied]), frame.facts)
        assertEquals("atMs at $tMs", clampedMs, frame.atMs)
        assertEquals("sinceMoveMs at $tMs", if (applied == 0) clampedMs else clampedMs - stamps[applied - 1], frame.sinceMoveMs)
        assertEquals("ended at $tMs", clampedMs >= endMs, frame.ended)
    }
}
