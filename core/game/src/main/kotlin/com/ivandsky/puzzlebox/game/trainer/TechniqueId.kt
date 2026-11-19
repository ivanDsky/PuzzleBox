package com.ivandsky.puzzlebox.game.trainer

/**
 * Stable identity of one solving technique ("line-lock", "naked-pair", ...), the key progress
 * and the [TechniqueIndex] file both store. Stable across releases: never an enum name or
 * ordinal, never renamed (a renamed id orphans every stored mark and index entry). Lowercase
 * letters, digits and `-` only, so an id is safe in the index file.
 */
@JvmInline
value class TechniqueId(val value: String) {
    override fun toString(): String = value
}
