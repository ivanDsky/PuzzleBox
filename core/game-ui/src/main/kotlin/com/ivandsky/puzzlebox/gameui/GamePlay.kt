package com.ivandsky.puzzlebox.gameui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.CoroutineScope

/**
 * One game's play surface (ADR-0001: one game module + one registration). The play
 * feature drives every game through this seam and never names a game: it fetches the
 * daily, resolves the record identity, writes the record and derives the badge; the
 * game owns its session, its input rules and its screen.
 */
interface GamePlayUi {
    val gameId: GameId

    /** A fresh controller for one adopted session; [env] is how it talks back. */
    fun createController(env: PlayEnvironment): GamePlayController

    /**
     * The game's play screen. [controller] is one this UI created; [status] is the
     * shared fetch state (Loading / Unavailable / Ready + degrade banner). [onSolved]
     * carries the frozen result facts once per completion — including a restored
     * completed session, which shows its win.
     */
    @Composable
    fun Screen(
        controller: GamePlayController,
        status: PlayBoardStatus,
        layout: PlayLayout,
        onRetry: () -> Unit,
        onSolved: (timeMs: Long, hintsUsed: Int) -> Unit,
        modifier: Modifier,
    )
}

/**
 * What a controller may call back into: [scope] is the play session's lifetime (it
 * outlives the play entry's composition across the result route), [onSessionChanged]
 * receives the game's session-codec bytes on every committed change, and [onWon] the
 * one completion record of a win transition.
 */
class PlayEnvironment(
    val scope: CoroutineScope,
    val onSessionChanged: (ByteArray) -> Unit,
    val onWon: (CompletionRecord) -> Unit,
)

/** A game's live session holder, as the game-agnostic play feature sees it. */
interface GamePlayController {
    /** Whether the live session is finished (a fresh entry must then fetch again). */
    val isComplete: Boolean

    /** Adopt today's fetched envelope under the resolved record identity [ref]. */
    fun adoptToday(puzzle: Puzzle, ref: PuzzleRef): Adopt

    /** Adopt a saved/replayed session blob through the game's own codec; null = unreadable. */
    fun adoptRestored(bytes: ByteArray): Restored?

    /** A fresh session of the SAME puzzle (result's "Play again", A9). */
    fun replay()
}

/** The outcome of [GamePlayController.adoptToday]. */
sealed interface Adopt {
    data object Adopted : Adopt

    /** The envelope is not playable (A15/A16 payload untrusted); [message] is the copy. */
    data class Rejected(val message: String) : Adopt
}

/** A restored session's record identity (null when the session carries none). */
data class Restored(val ref: PuzzleRef?)

/** Every registered game's play UI, by id. A duplicate id is a wiring bug. */
class GamePlayRegistry(games: List<GamePlayUi>) {
    private val byId: Map<GameId, GamePlayUi> = buildMap {
        games.forEach { game ->
            require(put(game.gameId, game) == null) { "play UI already registered for ${game.gameId}" }
        }
    }

    operator fun get(id: GameId): GamePlayUi? = byId[id]
}
