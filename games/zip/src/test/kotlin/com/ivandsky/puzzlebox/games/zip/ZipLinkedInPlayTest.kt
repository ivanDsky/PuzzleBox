package com.ivandsky.puzzlebox.games.zip

import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.linkedin.LinkedInPlayValue
import com.ivandsky.puzzlebox.game.linkedin.LinkedInSolve
import com.ivandsky.puzzlebox.game.replay.MoveKind
import com.ivandsky.puzzlebox.game.replay.ReplayCodec
import com.ivandsky.puzzlebox.game.replay.ReplayOutcome
import com.ivandsky.puzzlebox.game.replay.ReplayRecord
import com.ivandsky.puzzlebox.game.replay.StampedMove
import com.ivandsky.puzzlebox.games.zip.ZipReplayMove.Extend
import com.ivandsky.puzzlebox.games.zip.ZipReplayMove.Hint
import com.ivandsky.puzzlebox.games.zip.ZipReplayMove.Undo
import com.ivandsky.puzzlebox.games.zip.codec.ZipCodec
import com.ivandsky.puzzlebox.games.zip.engine.ZipMove
import com.ivandsky.puzzlebox.model.SolveReplay
import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** The Zip LinkedIn encoder against the captured win request, directly and through [zipPlugin]'s replay fold. */
class ZipLinkedInPlayTest {

    private val capture: JsonObject = Json.parseToJsonElement(
        ZipLinkedInPlayTest::class.java.getResourceAsStream("/fixtures/zip-update-game-state-win.request.json")!!
            .bufferedReader().readText(),
    ).jsonObject
    private val capturedPath: List<Int> = capturedState("zipGamePathBinding").jsonArray.map { it.jsonPrimitive.content.toInt() }

    /** The captured 6x6 board: open grid, first and last waypoint of the captured path. */
    private val puzzle = ZipPuzzle(
        ZipTestSupport.variantOf(6).id,
        ZipBoard(6, emptySet(), listOf(Cell(capturedPath.first()), Cell(capturedPath.last()))),
        capturedPath.map(::Cell),
    )
    private val envelope = Puzzle(ZipDefinition.id, puzzle.variant, ZipCodec.VERSION, ZipCodec.encode(puzzle))
    private val initial = ZipSession(ref = null, puzzle = puzzle)

    private fun capturedState(key: String) =
        capture.getValue("states").jsonArray.map { it.jsonObject }.first { it.getValue("key").jsonPrimitive.content == key }.getValue("value")

    private fun solveOf(vararg moves: ZipReplayMove, endMs: Long = 90_000): SolveReplay {
        val stamped = moves.mapIndexed { i, move -> StampedMove(1_000L * (i + 1), move.toReplayMove()) }
        val stored = checkNotNull(ReplayCodec.encode(ReplayRecord(ZipDefinition.id, ReplayOutcome.Solved, endMs, stamped)))
        return checkNotNull(SolveReplay.of(stored))
    }

    private fun walk(vararg idx: Int): Array<ZipReplayMove> = idx.map { Extend(Cell(it)) }.toTypedArray()

    private fun solvedSession(): ZipSession = capturedPath.fold(initial) { s, i -> s.withMove(ZipMove.Extend(Cell(i)), Instant.EPOCH) }

    @Test
    fun `a solved session encodes to the key names and shape of the captured win request`() {
        val play = checkNotNull(ZipLinkedInPlay.encode(LinkedInSolve(listOf(solvedSession()), listOf(MoveKind.Normal))))

        assertEquals(capturedState("zipGamePathBinding").jsonArray.map { it.jsonPrimitive.content }, (play.states["zipGamePathBinding"] as LinkedInPlayValue.Strings).values)
        assertEquals(capturedState("zipNumBacktracksBinding").jsonPrimitive.int, (play.states["zipNumBacktracksBinding"] as LinkedInPlayValue.Number).value)
        assertEquals(setOf("zipGamePathBinding", "zipNumBacktracksBinding"), play.states.keys)
        val arguments = capture.getValue("serverRequest").jsonObject.getValue("requestedArguments").jsonObject.getValue("payload").jsonObject
        assertEquals(
            play.payloadKeys,
            listOf("playRecord", "numBacktracks").associateWith { arguments.getValue(it).jsonObject.getValue("key").jsonPrimitive.content },
        )
        assertEquals(true, play.mistakeFree)
    }

    @Test
    fun `a replay of the same solve gives the same record through linkedInPlayOf`() {
        val direct = ZipLinkedInPlay.encode(LinkedInSolve(listOf(solvedSession()), listOf(MoveKind.Normal)))

        val viaReplay = zipPlugin.linkedInPlayOf(envelope, solveOf(*walk(*capturedPath.toIntArray())))

        assertNotNull(viaReplay)
        assertEquals(direct, viaReplay)
    }

    @Test
    fun `an undo in the log makes the record not mistake-free but keeps the same path`() {
        // undo drops cell 2, which is then walked again, so the final path is the captured one
        val moves = arrayOf(*walk(*capturedPath.take(3).toIntArray()), Undo, *walk(capturedPath[2]), *walk(*capturedPath.drop(3).toIntArray()))

        val play = checkNotNull(zipPlugin.linkedInPlayOf(envelope, solveOf(*moves)))

        assertEquals(false, play.mistakeFree)
        assertEquals(capturedPath.map(Int::toString), (play.states["zipGamePathBinding"] as LinkedInPlayValue.Strings).values)
    }

    @Test
    fun `a hint in the log makes the record not mistake-free`() {
        val moves = arrayOf(*walk(capturedPath[0]), Hint, *walk(*capturedPath.drop(1).toIntArray()))

        assertEquals(false, zipPlugin.linkedInPlayOf(envelope, solveOf(*moves))?.mistakeFree)
    }

    @Test
    fun `a final session with a mistake is not mistake-free even if no tick was one`() {
        val flawed = solvedSession().copy(mistakeCount = 1)

        assertEquals(false, ZipLinkedInPlay.encode(LinkedInSolve(listOf(flawed), listOf(MoveKind.Normal)))?.mistakeFree)
    }

    @Test
    fun `the final backtrack count is the numBacktracks state`() {
        val backtracked = solvedSession().copy(backtrackCount = 2)

        val play = checkNotNull(ZipLinkedInPlay.encode(LinkedInSolve(listOf(backtracked), listOf(MoveKind.Normal))))

        assertEquals(LinkedInPlayValue.Number(2), play.states["zipNumBacktracksBinding"])
    }

    @Test
    fun `a path that is not complete gives null`() {
        val partial = initial.withMove(ZipMove.Extend(Cell(capturedPath[0])), Instant.EPOCH)

        assertNull(ZipLinkedInPlay.encode(LinkedInSolve(listOf(partial), listOf(MoveKind.Normal))))
    }

    @Test
    fun `corrupt bytes give null and never throw`() {
        assertNull(zipPlugin.linkedInPlayOf(envelope, checkNotNull(SolveReplay.of(byteArrayOf(9, 9, 9, 9)))))
    }
}
