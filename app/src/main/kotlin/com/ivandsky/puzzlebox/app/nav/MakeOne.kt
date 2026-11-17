package com.ivandsky.puzzlebox.app.nav

import com.ivandsky.puzzlebox.create.api.EditorKey
import com.ivandsky.puzzlebox.domain.CreateDraft
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.navigation.Navigator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The game page's Make-one action (N10): stores a blank draft of a game in its default size and pushes its
 * Studio on the stack the door is on, so Back returns to the game page. A tap while a draft is still being
 * stored is dropped, so a double tap makes one draft.
 */
internal class MakeOne(
    private val createDraft: CreateDraft,
    private val games: GameRegistry,
    private val scope: CoroutineScope,
    private val navigator: Navigator,
) {
    private var creating: Job? = null

    operator fun invoke(game: GameId) {
        if (creating?.isActive == true) return
        val editor = games[game]?.editor ?: return
        creating = scope.launch { navigator.pushOnce(EditorKey(createDraft(game, editor.defaultVariant).id)) }
    }
}
