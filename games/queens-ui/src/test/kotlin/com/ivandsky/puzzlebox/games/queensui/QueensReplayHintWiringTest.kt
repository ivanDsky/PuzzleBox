package com.ivandsky.puzzlebox.games.queensui

import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.game.replay.GameReplay
import com.ivandsky.puzzlebox.games.queens.QueensDefinition
import com.ivandsky.puzzlebox.games.queens.QueensReplayMove
import com.ivandsky.puzzlebox.games.queens.QueensSession
import com.ivandsky.puzzlebox.games.queens.QueensSessionStep
import com.ivandsky.puzzlebox.games.queens.engine.hints.step.QueensStep
import com.ivandsky.puzzlebox.games.queens.queensModule
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.koin.core.qualifier.qualifier
import org.koin.dsl.koinApplication
import org.koin.dsl.module

/**
 * Ticket 47b: with the Queens UI module and the Queens module in one graph, the plugin the registry collects replays a
 * recorded hint with the option the play UI gets, read at each mark. One Koin case: the modules are top-level vals and
 * Koin caches their singletons across containers, so the flag is flipped inside the one container instead.
 */
class QueensReplayHintWiringTest {

    private val fresh = QueensSession(ref = null, puzzle = QueensPlayFixtures.puzzle())

    private fun live(firstMove: Boolean): Set<Int> =
        when (val step = QueensSessionStep.next(fresh, QueensPlayFixtures.solution.toSet(), firstMove)) {
            is QueensStep.Deduction -> when (val result = step.result) {
                is QueensStep.Deduction.Result.Cross -> result.cells.mapTo(HashSet()) { it.idx }
                is QueensStep.Deduction.Result.LastCell -> setOf(result.cell.idx)
            }
            else -> error("fixture does not open on a deduction: $step")
        }

    @Test
    fun `the registered plugin replays a recorded hint with the option the play UI gets, read at each mark`() {
        val flags = object : ReleaseFlags {
            override val enabled = MutableStateFlow(emptySet<ReleaseFlag>())
        }
        val koin = koinApplication { modules(queensModule, queensUiModule, module { single<ReleaseFlags> { flags } }) }.koin
        val plugin = koin.get<GamePlugin<*, *, *>>(qualifier(QueensDefinition.id.value))
        @Suppress("UNCHECKED_CAST")
        val replay = checkNotNull(plugin.replay) as GameReplay<QueensSession>
        fun hinted() = replay.markOf(fresh, QueensReplayMove.Hint.toReplayMove(), fresh, null).hinted

        assertNotEquals(live(firstMove = false), live(firstMove = true))
        assertEquals(live(firstMove = false), hinted())

        flags.enabled.value = setOf(ReleaseFlag.HintFirstMove)
        assertEquals(live(firstMove = true), hinted())

        flags.enabled.value = emptySet()
        assertEquals(live(firstMove = false), hinted())
    }
}
