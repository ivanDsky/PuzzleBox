package com.ivandsky.puzzlebox.gameui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.game.RendererModel
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

    /** The game's brand on every surface (spec A2); a game that registers none reads neutral. */
    val brand: GameBrand get() = GameBrand.Neutral

    /** A fresh controller for one adopted session; [env] is how it talks back. */
    fun createController(env: PlayEnvironment): GamePlayController

    /**
     * The game's lessons, in order (spec A11): each is one instruction line and a tiny
     * puzzle. [TutorialHost] plays them through [Board] on a sandboxed controller.
     */
    val tutorial: List<TutorialStep>

    /**
     * The game's board alone: no chrome, no fallback. [controller] is one this UI
     * created; nothing draws until it holds a session. [Screen] draws its daily board
     * through this, and [TutorialHost] draws each lesson through it, so input and look
     * are the same in both.
     */
    @Composable
    fun Board(controller: GamePlayController, modifier: Modifier)

    /**
     * One short line for the hub's Up-next card read from [today]'s board: a theme, a
     * progress count. Null when the game has none or the board can't be read. Pure, no
     * I/O: the hub calls it off the main thread.
     */
    fun hubLine(today: BoardPreview): String? = null

    /** Retired thumbnail model; null means the hub shows the glyph. Removed once no game overrides it. */
    fun previewModel(preview: BoardPreview): RendererModel? = null

    /** Retired thumbnail; draws nothing unless a game still overrides it. */
    @Composable
    fun BoardThumbnail(model: RendererModel, modifier: Modifier) {}

    /**
     * The game's Clear over [controller] (A14): null when the game has none, which hides
     * the action bar's Clear. [Screen] hands the same seam to [PlayChrome].
     */
    fun clearFor(controller: GamePlayController): PlayClear? = null

    /**
     * The game's settings for the play top bar's overflow menu (A14), read on each
     * composition so a toggle shows its current state. Empty hides the menu.
     */
    fun overflowFor(controller: GamePlayController): List<PlayOverflowItem> = emptyList()

    /**
     * The game's play screen under the route's top bar: the board, its caption and the
     * [PlayChrome] action bar, whose Clear is [clearFor]. [controller] is one this UI
     * created; [status] is the
     * shared fetch state (Loading / Unavailable / Ready + degrade banner). [onSolved]
     * carries the frozen result facts once per completion, after the [PlaySolveFrame]
     * moment, including a restored completed session, which hands off at once.
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

    /** The brand registered for [id]; an unknown id falls back to [GameBrand.Neutral]. */
    fun brandOf(id: GameId): GameBrand = byId[id]?.brand ?: GameBrand.Neutral
}
