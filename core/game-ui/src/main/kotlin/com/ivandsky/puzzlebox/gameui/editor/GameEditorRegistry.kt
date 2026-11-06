package com.ivandsky.puzzlebox.gameui.editor

import com.ivandsky.puzzlebox.model.GameId

/**
 * GameId → [GameEditorUi] (ADR-0019), the same shape as
 * [com.ivandsky.puzzlebox.gameui.GamePlayRegistry]: one registration per game, and a
 * duplicate id is a wiring bug that throws at construction.
 */
class GameEditorRegistry(editors: List<GameEditorUi>) {
    private val byId: Map<GameId, GameEditorUi> = buildMap {
        editors.forEach { editor ->
            val id = editor.gameId
            require(put(id, editor) == null) { "editor UI already registered for ${id.value}" }
        }
    }

    val ids: Set<GameId> get() = byId.keys

    operator fun get(id: GameId): GameEditorUi? = byId[id]
}
