package com.ivandsky.puzzlebox.gameui

/** One stat chip under the hero time: [tag] is its test tag, [label] sits under the [value]; a non-null [onClick] makes it a button. */
data class ResultHeroChip(val tag: String, val value: String, val label: String, val onClick: (() -> Unit)? = null)
