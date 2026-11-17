package com.ivandsky.puzzlebox.games.tangoui

import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.replay.GameReplay
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoDefinition
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import com.ivandsky.puzzlebox.games.tango.TangoReplay
import com.ivandsky.puzzlebox.games.tango.TangoReplayMove
import com.ivandsky.puzzlebox.games.tango.TangoSession
import com.ivandsky.puzzlebox.games.tango.tangoModule
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test
import org.koin.core.qualifier.qualifier
import org.koin.dsl.koinApplication
import org.koin.dsl.module

/**
 * Ticket 47b: with the Tango UI module and the Tango module in one graph, the plugin the registry collects replays a
 * recorded hint with the option the play UI gets, read at each mark. One Koin case, as in [TangoHintModeWiringTest]:
 * the modules are top-level vals whose singletons Koin caches across containers.
 *
 * On a bare 6×6 with row 0 `SS....` and row 3 `SMSMS.` the legacy order hints cell 2 (a pair) and the first-move
 * order cell 23 (a full line to count).
 */
class TangoReplayHintWiringTest {

    private val entries = listOf(
        TangoReplayMove.Place(0, TangoCell.SUN), TangoReplayMove.Place(1, TangoCell.SUN),
        TangoReplayMove.Place(18, TangoCell.SUN), TangoReplayMove.Place(19, TangoCell.MOON),
        TangoReplayMove.Place(20, TangoCell.SUN), TangoReplayMove.Place(21, TangoCell.MOON),
        TangoReplayMove.Place(22, TangoCell.SUN),
    )

    private val board = entries.foldIndexed(
        TangoSession(ref = null, puzzle = TangoPuzzle(VariantId("tango/n6"), 6, emptyMap(), emptyList())),
    ) { i, session, move -> checkNotNull(TangoReplay.step(session, move.toReplayMove(), 1_000L * (i + 1))) }

    @Test
    fun `the registered plugin replays a recorded hint with the option the play UI gets, read at each mark`() {
        val flags = object : ReleaseFlags {
            override val enabled = MutableStateFlow(emptySet<ReleaseFlag>())
        }
        val koin = koinApplication { modules(tangoModule, tangoUiModule, module { single<ReleaseFlags> { flags } }) }.koin
        val plugin = koin.get<GamePlugin<*, *, *>>(qualifier(TangoDefinition.id.value))
        @Suppress("UNCHECKED_CAST")
        val replay = checkNotNull(plugin.replay) as GameReplay<TangoSession>
        fun hinted() = replay.markOf(board, TangoReplayMove.Hint.toReplayMove(), board, null).hinted

        assertEquals(setOf(2), hinted())

        flags.enabled.value = setOf(ReleaseFlag.HintFirstMove)
        assertEquals(setOf(23), hinted())

        flags.enabled.value = emptySet()
        assertEquals(setOf(2), hinted())
    }
}
