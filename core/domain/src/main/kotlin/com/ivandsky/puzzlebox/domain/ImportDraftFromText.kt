package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.repository.DraftRepository
import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.editor.NotationParse
import com.ivandsky.puzzlebox.game.editor.NotationText
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Whole-text import (spec A23): parse on dispatchers.default, and only a parse that succeeds
 * touches storage — one [CreateDraft] row for the title rule, then one upsert that swaps in
 * the parsed blob and marks the row imported.
 */
class ImportDraftFromText(
    private val registry: GameRegistry,
    private val createDraft: CreateDraft,
    private val drafts: DraftRepository,
    private val dispatchers: DispatcherProvider,
) {
    /**
     * NotationText.parse on dispatchers.default (the cap is its first step, so over-cap text is never
     * parsed). Parsed → createDraft(gameId, variant) for the title rule, then one upsert of that row
     * with blob = parsed.encode() and imported = true. Every other outcome writes nothing. The two
     * writes are not cancellable, so a cancel leaves no row or one complete imported row, never a
     * blank "Untitled" one.
     */
    suspend operator fun invoke(text: String): ImportResult = withContext(dispatchers.default) {
        when (val parse = NotationText.parse(text, registry)) {
            is NotationParse.Parsed -> {
                val draft = parse.draft
                val created = withContext(NonCancellable) {
                    createDraft(draft.gameId, draft.variant).also {
                        drafts.upsert(it.copy(blob = draft.encode(), imported = true))
                    }
                }
                ImportResult.Imported(created.id)
            }
            is NotationParse.TooLarge -> ImportResult.TooLarge(parse.bytes, parse.line, parse.col)
            is NotationParse.UnknownGame -> ImportResult.UnknownGame(parse.gameId)
            is NotationParse.UnsupportedVersion -> ImportResult.UnsupportedVersion(parse.found, parse.maxKnown)
            is NotationParse.Malformed -> ImportResult.Malformed(parse.line, parse.col, parse.message)
        }
    }
}
