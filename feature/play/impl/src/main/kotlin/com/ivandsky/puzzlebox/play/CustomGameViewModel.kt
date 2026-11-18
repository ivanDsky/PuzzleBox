package com.ivandsky.puzzlebox.play

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.data.repository.UserPreferencesRepository
import com.ivandsky.puzzlebox.play.api.CustomGameKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One friend's or custom puzzle's board entry: opens the key's slot in the [PlaySessionStore] and, with
 * PlayFeel on, gives it the daily's tutorial gate ([TutorialGateHolder]). PlayFeel off keeps the gate
 * closed and offers no How to play, as before. A resumed saved play follows the same rule: the gate reads
 * only the game's seen flag.
 */
internal class CustomGameViewModel(
    key: CustomGameKey,
    store: PlaySessionStore,
    prefs: UserPreferencesRepository,
    feel: PlayFeel,
    howToPlayRules: HowToPlayRules,
) : ViewModel() {

    /** The slot's live play; null when the key's game is unplayable in this build. */
    val play: ActivePlay? = store.open(key)

    private val gate = if (feel.enabled) TutorialGateHolder(play, prefs, viewModelScope, howToPlayRules) else null

    val tutorial: StateFlow<TutorialGate> = gate?.gate ?: MutableStateFlow(TutorialGate.Closed)

    /** Null with PlayFeel off: the bar then has no How to play. */
    val howToPlay: (() -> Unit)? = gate?.let { it::howToPlay }

    fun closeTutorial() {
        gate?.close()
    }

    /** The rules sheet's copy while it shows; always null with PlayFeel off. */
    val rules: StateFlow<RulesSheetCopy?> = gate?.rules ?: MutableStateFlow<RulesSheetCopy?>(null).asStateFlow()

    fun dismissRules() {
        gate?.dismissRules()
    }

    fun replayTutorial() {
        gate?.replayTutorial()
    }
}
