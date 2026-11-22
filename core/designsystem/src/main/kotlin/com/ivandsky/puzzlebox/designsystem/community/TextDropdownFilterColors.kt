package com.ivandsky.puzzlebox.designsystem.community

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * The colours of a [TextDropdownFilter], per state: [contentColor] while nothing is picked, [setContentColor] once a value
 * is, and [setUnderlineColor] for the underline that marks a set filter (null draws no underline). Built by
 * [TextDropdownFilterDefaults.colors] or [TextDropdownFilterDefaults.quietColors].
 */
@Immutable
class TextDropdownFilterColors(
    private val contentColor: Color,
    private val setContentColor: Color,
    private val setUnderlineColor: Color?,
) {
    fun contentColor(set: Boolean): Color = if (set) setContentColor else contentColor

    /** The underline of a set filter, or null when this look draws none. */
    fun underlineColor(set: Boolean): Color? = if (set) setUnderlineColor else null

    // Value equality keeps TextDropdownFilter skippable when the defaults return a new instance.
    override fun equals(other: Any?): Boolean =
        other is TextDropdownFilterColors &&
            contentColor == other.contentColor &&
            setContentColor == other.setContentColor &&
            setUnderlineColor == other.setUnderlineColor

    override fun hashCode(): Int {
        var result = contentColor.hashCode()
        result = 31 * result + setContentColor.hashCode()
        result = 31 * result + (setUnderlineColor?.hashCode() ?: 0)
        return result
    }
}
