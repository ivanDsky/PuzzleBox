package com.ivandsky.puzzlebox.create

import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode

/**
 * The Custom list: the shown [drafts], newest edit first, and [draftCount] of them. [games] are the games with an
 * editor in this build, in editor registration order: the New puzzle sheet's rows and, with nothing stored or
 * published at all ([isEmpty]), the empty state's. [deleted] is the draft just deleted while its Undo is offered.
 * [published] are the published puzzles, newest first (no published draft is a draft row). [restoring] is true while
 * the cloud's first restore on this device runs (A34): the empty state says the drafts are coming back instead of
 * its first-time copy.
 */
data class CreateUiState(
    val loading: Boolean = true,
    val drafts: List<DraftRow> = emptyList(),
    val draftCount: Int = 0,
    val games: List<NewGame> = emptyList(),
    val deleted: Deleted? = null,
    val published: List<PublishedRow> = emptyList(),
    val restoring: Boolean = false,
) {

    /** Nothing stored or published yet: the first-time empty state instead of the lists (spec A3). */
    val isEmpty: Boolean get() = !loading && draftCount == 0 && published.isEmpty()

    /**
     * One published puzzle's row: its game's [brand] tile, [title], its share [code], its
     * friends' [solves] (the last fetched count; null until first fetched), the "Author 1:42"
     * [meta] ("Unpublished" once [unpublished]) and "published 2 h ago" [published].
     */
    data class PublishedRow(
        val code: ShareCode,
        val gameId: GameId,
        val brand: GameBrand,
        val title: String,
        val meta: String,
        val published: String,
        val unpublished: Boolean = false,
        val solves: Int? = null,
    )

    /**
     * One draft's row: its game's [brand] tile, [title], "{Game} · {size}" [meta], [imported]
     * when the text importer made it, "edited 2 h ago" [edited], and the background [verdict];
     * Checking until it lands, null when this build cannot open the draft.
     */
    data class DraftRow(
        val id: String,
        val gameId: GameId,
        val brand: GameBrand,
        val title: String,
        val meta: String,
        val edited: String,
        val verdict: Verdict?,
        val imported: Boolean = false,
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

    /** A just-deleted draft: its [id] and the [title] the Undo snackbar names. */
    data class Deleted(val id: String, val title: String)
}
