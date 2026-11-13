package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.data.match.Match
import com.ivandsky.puzzlebox.domain.CreateMatch
import com.ivandsky.puzzlebox.domain.CreateOutcome
import com.ivandsky.puzzlebox.domain.DuelSettings
import com.ivandsky.puzzlebox.domain.ranks.GetPlayerActions
import com.ivandsky.puzzlebox.domain.ranks.PlayerActions
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The player sheet's state holder: it only maps [GetPlayerActions] (one tier read per [open]) and runs the challenge.
 * Every gate lives in the use case, so a locked action is just drawn locked here.
 */
internal class PlayerSheetViewModel(
    private val getActions: GetPlayerActions,
    private val createMatch: CreateMatch,
    private val registry: GameRegistry,
    private val playRegistry: GamePlayRegistry,
) : ViewModel() {

    /**
     * The row tapped: [uid], [name] and [place] as the board shows them, on the board of [game] (null on Overall) at [day]
     * (null when the window is a week or month, which has no single puzzle).
     */
    data class Target(val uid: String, val name: String, val place: Standing, val game: GameId?, val day: LocalDate?) {
        /** The one puzzle the row is about, when it is one. */
        val board: LeaderboardRef? get() = if (game != null && day != null) LeaderboardRef(game.value, day) else null
    }

    /** A game the player can be challenged at: [icon] is its brand glyph. */
    data class Game(val id: GameId, val name: String, val icon: ImageVector)

    sealed interface Challenge {
        data object Idle : Challenge

        data object Sending : Challenge

        /** The invite is out: the sheet says "Sent to [name]". */
        data class Sent(val name: String) : Challenge

        data class Failed(val message: String) : Challenge
    }

    /** [actions] is null until the tier read answers. [games] are the choices a Challenge tap picks from: every game on Overall, the board's own game alone otherwise. */
    data class State(
        val target: Target,
        val actions: PlayerActions? = null,
        val games: List<Game> = emptyList(),
        val challenge: Challenge = Challenge.Idle,
    )

    private val _state = MutableStateFlow<State?>(null)

    /** Null while no sheet is open. */
    val state: StateFlow<State?> = _state.asStateFlow()

    private var loading: Job? = null

    /** Opens the sheet on [target]: one tier read. Opening the target already open does nothing. */
    fun open(target: Target) {
        if (_state.value?.target == target) return
        loading?.cancel()
        _state.value = State(target, games = gamesOf(target))
        loading = viewModelScope.launch {
            val actions = getActions(target.uid, target.board)
            _state.update { it?.takeIf { open -> open.target == target }?.copy(actions = actions) ?: it }
        }
    }

    fun close() {
        loading?.cancel()
        _state.value = null
    }

    /** Sends [game]'s duel to the sheet's player; only once actions say Challenge is allowed, and once per open. */
    fun challenge(game: GameId) {
        val open = _state.value ?: return
        if (open.actions?.challenge != PlayerActions.Challenge.Allowed || open.challenge is Challenge.Sending || open.challenge is Challenge.Sent) return
        _state.value = open.copy(challenge = Challenge.Sending)
        viewModelScope.launch {
            val outcome = createMatch(duelOf(game), invitedUid = open.target.uid)
            _state.update { current -> current?.takeIf { it.target == open.target }?.copy(challenge = outcome.toChallenge(open.target.name)) ?: current }
        }
    }

    private fun duelOf(game: GameId) = DuelSettings(
        count = 1,
        games = setOf(game),
        todayOnly = false,
        deadlineHours = Match.DEADLINE_CHOICES.first(),
        includeFriends = false,
        pastedCodes = emptyList(),
    )

    private fun gamesOf(target: Target): List<Game> {
        val ids = target.game?.let(::listOf) ?: registry.all.map { it.definition.id }
        return ids.map { Game(it, registry.displayName(it), playRegistry.brandOf(it).glyph) }
    }

    private fun CreateOutcome.toChallenge(name: String): Challenge = when (this) {
        is CreateOutcome.Created -> Challenge.Sent(name)
        CreateOutcome.SignedOut -> Challenge.Failed("Sign in to challenge")
        CreateOutcome.Offline -> Challenge.Failed("You're offline")
        CreateOutcome.DailyCapReached -> Challenge.Failed("Daily duel limit reached")
        CreateOutcome.NoPuzzles -> Challenge.Failed("No puzzle to duel right now")
        is CreateOutcome.Failed -> Challenge.Failed("Couldn't send the challenge")
    }
}
