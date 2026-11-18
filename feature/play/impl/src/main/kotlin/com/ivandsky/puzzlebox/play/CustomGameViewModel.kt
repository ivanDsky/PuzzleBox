package com.ivandsky.puzzlebox.play

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.data.repository.UserPreferencesRepository
import com.ivandsky.puzzlebox.play.api.CustomGameKey
import kotlinx.coroutines.flow.StateFlow

/**
 * One friend's or custom puzzle's board entry: opens the key's slot in the [PlaySessionStore] and gives it the
 * daily's tutorial gate ([TutorialGateHolder]). A resumed saved play follows the same rule: the gate reads only the
 * game's seen flag.
 */
internal class CustomGameViewModel(
    key: CustomGameKey,
    store: PlaySessionStore,
    prefs: UserPreferencesRepository,
    howToPlayRules: HowToPlayRules,
) : ViewModel() {

    /** The slot's live play; null when the key's game is unplayable in this build. */
    val play: ActivePlay? = store.open(key)

    private val gate = TutorialGateHolder(play, prefs, viewModelScope, howToPlayRules)

    val tutorial: StateFlow<TutorialGate> = gate.gate

    /** The rules sheet's copy while it shows. */
    val rules: StateFlow<RulesSheetCopy?> = gate.rules

    fun howToPlay() = gate.howToPlay()

    fun closeTutorial() = gate.close()

    fun dismissRules() = gate.dismissRules()

    fun replayTutorial() = gate.replayTutorial()
}
