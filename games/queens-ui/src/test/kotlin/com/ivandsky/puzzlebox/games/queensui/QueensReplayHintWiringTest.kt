package com.ivandsky.puzzlebox.games.queensui

import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.game.replay.GameReplay
import com.ivandsky.puzzlebox.games.queens.QueensDefinition
import com.ivandsky.puzzlebox.games.queens.QueensReplayMove
import com.ivandsky.puzzlebox.games.queens.QueensSession
import com.ivandsky.puzzlebox.games.queens.QueensSessionStep
import com.ivandsky.puzzlebox.games.queens.engine.hints.step.QueensStep
import com.ivandsky.puzzlebox.games.queens.queensModule
import org.junit.Assert.assertEquals
import org.junit.Test
import org.koin.core.qualifier.qualifier
import org.koin.dsl.koinApplication

/** With the Queens UI module and the Queens module in one graph, the plugin the registry collects replays a recorded hint with the cells a live press shows. */
class QueensReplayHintWiringTest {

    private val fresh = QueensSession(ref = null, puzzle = QueensPlayFixtures.puzzle())

    private fun live(): Set<Int> =
        when (val step = QueensSessionStep.next(fresh, QueensPlayFixtures.solution.toSet())) {
            is QueensStep.Deduction -> when (val result = step.result) {
                is QueensStep.Deduction.Result.Cross -> result.cells.mapTo(HashSet()) { it.idx }
                is QueensStep.Deduction.Result.LastCell -> setOf(result.cell.idx)
            }
            else -> error("fixture does not open on a deduction: $step")
        }

    @Test
    fun `the registered plugin replays a recorded hint with the cells a live press shows`() {
        val koin = koinApplication { modules(queensModule, queensUiModule) }.koin
        val plugin = koin.get<GamePlugin<*, *, *>>(qualifier(QueensDefinition.id.value))
        @Suppress("UNCHECKED_CAST")
        val replay = checkNotNull(plugin.replay) as GameReplay<QueensSession>

        assertEquals(live(), replay.markOf(fresh, QueensReplayMove.Hint.toReplayMove(), fresh, null).hinted)
    }
}
