package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.data.repository.UserPreferencesRepository
import com.ivandsky.puzzlebox.gameui.TutorialSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * One board's tutorial gate (spec A11), owned by the entry's ViewModel and run on its [scope]: a [play]
 * whose game has lessons that [prefs] has not marked seen opens them in place of the board once that read
 * lands (the board never waits for it, so a first play may show one frame first). [howToPlay] reopens
 * them over the live play; [close] marks the game seen. The open gate lives here, so a rotation keeps its step.
 */
internal class TutorialGateHolder(
    private val play: ActivePlay?,
    private val prefs: UserPreferencesRepository,
    private val scope: CoroutineScope,
) {
    private val _gate = MutableStateFlow<TutorialGate>(TutorialGate.Closed)
    val gate: StateFlow<TutorialGate> = _gate.asStateFlow()

    /** The player has opened or closed the tutorial, so a late first-play read leaves it alone. */
    private var touched = false

    init {
        if (play != null && play.ui.tutorial.isNotEmpty()) {
            scope.launch {
                val seen = prefs.tutorialSeen(play.gameId).first()
                if (!seen && !touched) _gate.value = TutorialGate.Open(TutorialSession(play.ui.tutorial))
            }
        }
    }

    /** A no-op without lessons or while a tutorial is already open. */
    fun howToPlay() {
        val lessons = play?.ui?.tutorial ?: return
        if (lessons.isEmpty() || _gate.value is TutorialGate.Open) return
        touched = true
        _gate.value = TutorialGate.Open(TutorialSession(lessons))
    }

    /** The tutorial ended (finished or skipped): the game is marked seen and the board shows. */
    fun close() {
        val game = play?.gameId ?: return
        if (_gate.value !is TutorialGate.Open) return
        touched = true
        _gate.value = TutorialGate.Closed
        scope.launch { prefs.markTutorialSeen(game) }
    }
}
