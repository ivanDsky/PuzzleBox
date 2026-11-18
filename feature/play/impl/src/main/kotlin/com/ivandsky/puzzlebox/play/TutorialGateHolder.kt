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
 * them over the live play, or, for a game that has rules copy, opens the rules sheet there instead and
 * offers the tutorial from it; [close] marks the game seen. The open gate lives here, so a rotation keeps its step.
 */
internal class TutorialGateHolder(
    private val play: ActivePlay?,
    private val prefs: UserPreferencesRepository,
    private val scope: CoroutineScope,
    private val howToPlayRules: HowToPlayRules,
) {
    private val _gate = MutableStateFlow<TutorialGate>(TutorialGate.Closed)
    val gate: StateFlow<TutorialGate> = _gate.asStateFlow()

    private val _rules = MutableStateFlow<RulesSheetCopy?>(null)

    /** The rules sheet's copy while it shows, null while it is closed. It is not a [TutorialGate]: the board stays composed under it. */
    val rules: StateFlow<RulesSheetCopy?> = _rules.asStateFlow()

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

    /**
     * For a game that has rules copy, opens the rules sheet over the live play; otherwise (and for
     * a game with no rules) the tutorial opens as before. A no-op without lessons or while the sheet or the tutorial is already open.
     */
    fun howToPlay() {
        val lessons = play?.ui?.tutorial ?: return
        if (lessons.isEmpty() || _gate.value is TutorialGate.Open || _rules.value != null) return
        touched = true
        val copy = howToPlayRules.copyFor(play.gameId)
        if (copy != null) _rules.value = copy else _gate.value = TutorialGate.Open(TutorialSession(lessons))
    }

    /** Got it, a swipe, the scrim or Back: the sheet closes and the board plays on. */
    fun dismissRules() {
        _rules.value = null
    }

    /** Replay the tutorial: the sheet closes and the tutorial opens over the live play, as How to play does for a game without rules copy. */
    fun replayTutorial() {
        val lessons = play?.ui?.tutorial ?: return
        if (_rules.value == null) return
        _rules.value = null
        if (_gate.value !is TutorialGate.Open) _gate.value = TutorialGate.Open(TutorialSession(lessons))
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
