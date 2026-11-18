package com.ivandsky.puzzlebox.compete.duel

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.domain.LegPuzzleRead
import com.ivandsky.puzzlebox.domain.LoadLegPuzzle
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.withContext

/**
 * The boards of a duel's legs, from the leg's own puzzle ([LoadLegPuzzle]) and what its game draws as givens, never the
 * solution. A settled answer is kept, so a leg's board keeps its identity as the page recomposes; a read that failed is
 * asked again next time. The work runs on [DispatcherProvider.default], as opening a friend's puzzle checks it.
 */
internal class DuelLegBoards(
    private val loadPuzzle: LoadLegPuzzle,
    private val plays: GamePlayRegistry,
    private val dispatchers: DispatcherProvider,
) : LegBoards {
    private val settled = ConcurrentHashMap<String, LegBoard>()

    override suspend fun of(leg: LegRef): LegBoard {
        settled[leg.wire]?.let { return it }
        return withContext(dispatchers.default) {
            when (val read = loadPuzzle(leg)) {
                is LegPuzzleRead.Found -> {
                    val givens = plays[leg.gameId]?.replayBoard?.givens(read.puzzle)
                    (givens?.let(LegBoard::Of) ?: LegBoard.None).also { settled[leg.wire] = it }
                }
                LegPuzzleRead.Absent -> LegBoard.None.also { settled[leg.wire] = it }
                LegPuzzleRead.TakenDown -> LegBoard.TakenDown.also { settled[leg.wire] = it }
                // A daily leg that cannot be fetched is not "offline": only a friend's puzzle has a line for it.
                LegPuzzleRead.Unavailable -> if (leg is LegRef.Custom) LegBoard.Offline else LegBoard.None
            }
        }
    }
}
