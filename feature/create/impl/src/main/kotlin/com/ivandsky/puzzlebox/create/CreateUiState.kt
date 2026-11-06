package com.ivandsky.puzzlebox.create

import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.model.GameId

/**
 * The Create root: the stored drafts, newest edit first, and the [games] the New puzzle
 * sheet offers (each with an editor in this build, in editor registration order).
 */
data class CreateUiState(
    val drafts: List<DraftRow> = emptyList(),
    val games: List<NewGame> = emptyList(),
) {

    /** One draft's row: its [title] over "{Game} · {size}" [meta]. */
    data class DraftRow(val id: String, val title: String, val meta: String)

    /** A game the sheet offers: its [sizes] are exactly its editor's variants, in order. */
    data class NewGame(val id: GameId, val name: String, val sizes: List<Size>)

    /** One size choice: the editor's [variant] under its UI's [label] ("8×8"). */
    data class Size(val variant: VariantId, val label: String)
}
