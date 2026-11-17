package com.ivandsky.puzzlebox.games.tango

import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.linkedin.LinkedInPlayValue
import com.ivandsky.puzzlebox.game.linkedin.LinkedInSolve
import com.ivandsky.puzzlebox.game.replay.MoveKind
import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** The Tango LinkedIn encoder against the Tango #770 daily captures (presets from the page, board and order from the win). */
class TangoLinkedInPlayTest {

    private val win = request("tango-update-game-state-daily-win.request.json")
    private val opening = request("tango-update-game-state-daily-opening.request.json")
    private val capturedBoard = strings(win, "tangoBoardStateBinding")
    private val capturedOrder = strings(win, "tangoSolveOrderBinding")
    private val presets: List<Int> = Regex(""""presetCellIdxes":\[([0-9,]+)\]""")
        .find(resource("tango-page-daily-unsolved.rsc"))!!.groupValues[1].split(',').map(String::toInt)

    private val solved: List<TangoCell> = capturedBoard.map(::cellOf)
    private val puzzle = TangoPuzzle(VariantId("tango/n6"), 6, presets.associateWith { solved[it] }, emptyList())
    private val capturedPlacementOrder: List<Int> = capturedOrder.withIndex().filter { it.value != "-1" }.sortedBy { it.value.toInt() }.map { it.index }

    private fun cellOf(wire: String) = if (wire == "ZERO") TangoCell.SUN else TangoCell.MOON

    private fun resource(name: String) = TangoLinkedInPlayTest::class.java.getResourceAsStream("/fixtures/$name")!!.bufferedReader().readText()

    private fun request(name: String): JsonObject = Json.parseToJsonElement(resource(name)).jsonObject

    private fun strings(body: JsonObject, key: String): List<String> =
        body.getValue("states").jsonArray.map { it.jsonObject }.first { it.getValue("key").jsonPrimitive.content == key }
            .getValue("value").jsonArray.map { it.jsonPrimitive.content }

    /** One session per step; the last is complete when [complete]. A step is a cell and the value placed in it. */
    private fun boards(steps: List<Pair<Int, TangoCell>>, complete: Boolean = true): List<TangoSession> {
        var session = TangoSession(ref = null, puzzle = puzzle)
        return steps.mapIndexed { i, (idx, value) ->
            session = session.copy(placements = session.placements + (idx to value))
            if (complete && i == steps.lastIndex) session = session.copy(completedAt = Instant.EPOCH)
            session
        }
    }

    private fun solve(
        steps: List<Pair<Int, TangoCell>>,
        kinds: List<MoveKind> = steps.map { MoveKind.Normal },
        firstMoveMs: Long = 583,
        complete: Boolean = true,
    ) = LinkedInSolve(boards(steps, complete), kinds, steps.indices.map { firstMoveMs + it * 1_000L })

    private val capturedSteps get() = capturedPlacementOrder.map { it to solved[it] }

    @Test
    fun `a solve placing cells in the captured order round-trips the captured board and solve order`() {
        val play = checkNotNull(TangoLinkedInPlay.encode(solve(capturedSteps)))

        assertEquals(capturedBoard, (play.states["tangoBoardStateBinding"] as LinkedInPlayValue.Strings).values)
        assertEquals(capturedOrder, (play.states["tangoSolveOrderBinding"] as LinkedInPlayValue.Strings).values)
        assertEquals(setOf("tangoBoardStateBinding", "tangoSolveOrderBinding"), play.states.keys)
        assertEquals(mapOf("playRecord" to "tangoBoardStateBinding", "solveOrder" to "tangoSolveOrderBinding"), play.payloadKeys)
        assertEquals(true, play.mistakeFree)
    }

    @Test
    fun `a cell corrected late moves to the end of the solve order`() {
        val first = capturedSteps.first().first
        val steps = listOf(first to solved[first].other) + capturedSteps.drop(1) + (first to solved[first])
        val expected = (capturedPlacementOrder.drop(1) + first).withIndex().associate { it.value to it.index }

        val order = (checkNotNull(TangoLinkedInPlay.encode(solve(steps))).states["tangoSolveOrderBinding"] as LinkedInPlayValue.Strings).values

        assertEquals(List(36) { idx -> expected[idx]?.toString() ?: "-1" }, order)
        assertEquals("27", order[first])
    }

    @Test
    fun `a cell placed correctly, wrongly and correctly again ranks by its last correct placement`() {
        val (a, b) = capturedPlacementOrder.take(2)
        val steps = listOf(a to solved[a], b to solved[b], a to solved[a].other, a to solved[a]) + capturedSteps.drop(2)

        val order = (checkNotNull(TangoLinkedInPlay.encode(solve(steps))).states["tangoSolveOrderBinding"] as LinkedInPlayValue.Strings).values

        assertEquals("0", order[b])
        assertEquals("1", order[a])
    }

    @Test
    fun `the opening holds the presets and the first placed cell, with no solve order and the first move's ms`() {
        // The captured client first dropped a sun in cell 0, which the final board has as a moon.
        val first = capturedSteps.first()
        val play = checkNotNull(TangoLinkedInPlay.encode(solve(listOf(first.first to first.second.other) + capturedSteps)))

        val opened = checkNotNull(play.opening)

        assertEquals(mapOf("tangoBoardStateBinding" to LinkedInPlayValue.Strings(strings(opening, "tangoBoardStateBinding"))), opened.states)
        assertEquals(583L, opened.timeMs)
    }

    @Test
    fun `the opening time is never under one ms`() {
        assertEquals(1L, TangoLinkedInPlay.encode(solve(capturedSteps, firstMoveMs = 0))?.opening?.timeMs)
    }

    @Test
    fun `a final board that is not solved gives null`() {
        assertNull(TangoLinkedInPlay.encode(solve(capturedSteps.take(5), complete = false)))
    }

    @Test
    fun `an undo, a hint or a mistake tick makes the record not mistake-free`() {
        for (flaw in listOf(MoveKind.Undo, MoveKind.Hint, MoveKind.Mistake)) {
            val kinds = capturedSteps.mapIndexed { i, _ -> if (i == 3) flaw else MoveKind.Normal }

            assertEquals(false, TangoLinkedInPlay.encode(solve(capturedSteps, kinds))?.mistakeFree)
        }
    }

    @Test
    fun `a final session with a rejected drop is not mistake-free even if no tick was one`() {
        val flawed = solve(capturedSteps)
        val withMistake = LinkedInSolve(flawed.boards.dropLast(1) + flawed.final.copy(mistakeCount = 1), flawed.kinds, flawed.timesMs)

        assertEquals(false, TangoLinkedInPlay.encode(withMistake)?.mistakeFree)
    }

    @Test
    fun `the plugin carries the encoder`() {
        assertNotNull(tangoPlugin.linkedInPlay)
    }
}
