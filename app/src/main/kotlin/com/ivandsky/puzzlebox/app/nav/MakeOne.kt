package com.ivandsky.puzzlebox.app.nav

import com.ivandsky.puzzlebox.create.api.EditorKey
import com.ivandsky.puzzlebox.domain.CreateDraft
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.navigation.Navigator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The Make-one doors' action (N10): stores a blank draft of a game and pushes its Studio on the
 * stack the door is on, so Back returns to the game page or the Result it was opened from.
 *
 * The draft takes the asked size when the game's Studio offers it, else the game's default size:
 * the game page asks for none, and a played puzzle's size may be one the Studio has no board for.
 * A tap while a draft is still being stored is dropped, so a double tap makes one draft.
 */
internal class MakeOne(
    private val createDraft: CreateDraft,
    private val games: GameRegistry,
    private val scope: CoroutineScope,
    private val navigator: Navigator,
) {
    private var creating: Job? = null

    operator fun invoke(game: GameId, size: VariantId?) {
        if (creating?.isActive == true) return
        val editor = games[game]?.editor ?: return
        val variant = size?.takeIf { it in editor.variants } ?: editor.defaultVariant
        creating = scope.launch { navigator.pushOnce(EditorKey(createDraft(game, variant).id)) }
    }
}
