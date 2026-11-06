package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.model.Draft
import com.ivandsky.puzzlebox.data.repository.DraftRepository
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.model.GameId
import java.time.Instant
import kotlinx.coroutines.flow.first

/**
 * Creates and stores a blank [variant] draft for [GameId] (spec A2/A28): the game's own
 * new board with an empty history, under the next free "Untitled" title. Asking for a game
 * without an editor is a wiring bug and throws.
 */
class CreateDraft(
    private val registry: GameRegistry,
    private val drafts: DraftRepository,
    private val newId: () -> String,
    private val clock: () -> Instant,
) {
    /** Title "Untitled {displayName}", then "Untitled {displayName} 2", 3, … (smallest free). */
    suspend operator fun invoke(gameId: GameId, variant: VariantId): Draft {
        val editor = requireNotNull(registry[gameId]?.editor) { "game '${gameId.value}' has no editor" }
        val taken = drafts.observeAll().first().map { it.title }.toSet()
        val base = "Untitled ${registry.displayName(gameId)}"
        val title = if (base !in taken) base else generateSequence(2) { it + 1 }.map { "$base $it" }.first { it !in taken }
        val draft = Draft(
            id = newId(),
            gameId = gameId,
            variant = variant,
            draftFormatVersion = editor.draftFormatVersion,
            blob = EditorDraft.fresh(editor, variant).encode(),
            title = title,
            updatedAt = clock(),
            testSolve = null,
            reservedCode = null,
            imported = false,
            authorName = "",
            note = "",
        )
        drafts.upsert(draft)
        return draft
    }
}
