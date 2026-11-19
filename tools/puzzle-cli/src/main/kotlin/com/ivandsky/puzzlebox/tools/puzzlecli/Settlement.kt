package com.ivandsky.puzzlebox.tools.puzzlecli

import com.ivandsky.puzzlebox.game.trainer.TechniqueId

/** How one archive number settled in the index pass: walked into counts, or skipped with a reason. */
sealed interface Settlement {
    /** The walker's technique counts for the number; never empty (an empty walk is [Skip] Unwalkable). */
    data class Ok(val counts: Map<TechniqueId, Int>) : Settlement

    /** No index entry for the number: [kind] says why and [detail] is the one-line evidence. */
    data class Skip(val kind: SkipKind, val detail: String) : Settlement
}
