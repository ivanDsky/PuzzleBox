package com.ivandsky.puzzlebox.create

import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.model.GameId

/**
 * The Create root: the shown [drafts], newest edit first, narrowed by [query] and [filter]
 * only while [searchVisible] (more than 8 stored drafts); [draftCount] counts every stored
 * draft. [games] are the games with an editor in this build, in editor registration order:
 * the New puzzle sheet's rows and, with no drafts at all ([isEmpty]), the empty state's.
 * [deleted] is the draft just deleted while its Undo is offered.
 */
data class CreateUiState(
    val loading: Boolean = true,
    val drafts: List<DraftRow> = emptyList(),
    val draftCount: Int = 0,
    val games: List<NewGame> = emptyList(),
    val searchVisible: Boolean = false,
    val query: String = "",
    val filters: List<GameFilter> = emptyList(),
    val filter: GameId? = null,
    val deleted: Deleted? = null,
) {

    /** Nothing stored yet: the first-time empty state instead of the list (spec A3). */
    val isEmpty: Boolean get() = !loading && draftCount == 0

    /**
     * One draft's row: its game's [brand] tile, [title], "{Game} · {size}[ · imported]" [meta],
     * "edited 2 h ago" [edited], and the background [verdict]; Checking until it lands, null
     * when this build cannot open the draft.
     */
    data class DraftRow(
        val id: String,
        val gameId: GameId,
        val brand: GameBrand,
        val title: String,
        val meta: String,
        val edited: String,
        val verdict: Verdict?,
    )

    /**
     * A game to start: its [brand] tile, [name], one-line [rule] (null when none is written),
     * the [defaultSize] a one-tap start uses, and its [sizes], exactly its editor's variants in order.
     */
    data class NewGame(
        val id: GameId,
        val name: String,
        val brand: GameBrand,
        val rule: String?,
        val defaultSize: Size,
        val sizes: List<Size>,
    )

    /** One size choice: the editor's [variant] under its UI's [label] ("8×8"). */
    data class Size(val variant: VariantId, val label: String)

    /** A game filter chip: a game with [count] stored drafts. */
    data class GameFilter(val id: GameId, val name: String, val brand: GameBrand, val count: Int)

    /** A just-deleted draft: its [id] and the [title] the Undo snackbar names. */
    data class Deleted(val id: String, val title: String)
}
