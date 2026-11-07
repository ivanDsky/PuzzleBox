package com.ivandsky.puzzlebox.game.editor

import com.ivandsky.puzzlebox.game.VariantId

/** Capability: a resize that keeps content (Zip keeps the top-left, A5) instead of a blank board. */
interface DraftResize<D : Any> {
    fun resized(draft: D, variant: VariantId): D
}
