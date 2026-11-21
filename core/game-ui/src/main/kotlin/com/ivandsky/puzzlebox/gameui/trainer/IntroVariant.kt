package com.ivandsky.puzzlebox.gameui.trainer

/**
 * One variant of a technique, as its intro writes it down (spec §2.7). A technique has more than one variant when its
 * steps act on more than one kind of unit (Queens' Line lock: a line, or a color). Plain words throughout (spec A1).
 *
 * @property title the variant's short name, e.g. "Fill the line"; for a technique with one variant, its catalog name
 * @property rule the written rule, two or three plain sentences
 * @property spotIt one line on how to see that the variant applies
 * @property doIt one line on the move to make
 */
data class IntroVariant(
    val title: String,
    val rule: String,
    val spotIt: String,
    val doIt: String,
)
