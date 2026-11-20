package com.ivandsky.puzzlebox.app.widget

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Every size the widget draws with, worked out from the size the launcher gave it ([of]), so the widget fills 4 by 2 cells up to a
 * full tablet page. At [compact] (4 by 2, and any size whose rings stay under [NAMES_FROM]) the streak line sits top-left and the
 * button is full width. Above that the streak, rings and button are one centred block with the game names under the rings and a
 * pill button that hugs its words: the rings take the width, the text, icons, button and gaps grow with them, and whatever height
 * is still left is shared out as gaps around and inside the block so it sits evenly rather than floating in a band.
 */
internal data class WidgetLayout(
    val ring: Dp,
    val glyph: Dp,
    val showNames: Boolean,
    val compact: Boolean,
    val verticalPadding: Dp,
    val blockGap: Dp,
    val nameBand: Dp,
    val nameSize: TextUnit,
    val streak: StreakSizes,
    val button: ButtonSizes,
) {
    class StreakSizes(val line: Dp, val flame: Dp, val snowflake: Dp, val text: TextUnit, val freezeText: TextUnit)

    class ButtonSizes(val height: Dp, val text: TextUnit, val icon: Dp, val horizontalPadding: Dp)

    /** The height the whole block takes: everything from the top padding to the bottom one. */
    val height: Dp get() = verticalPadding * 2 + streak.line + blockGap * 2 + ring + nameBand + button.height

    companion object {
        /** The ring size at which a game's name is drawn under it, and where the chrome starts to grow. */
        val NAMES_FROM: Dp = 56.dp

        /** The 48dp touch-target floor; also the Up next pill's smallest height. */
        val BUTTON_HEIGHT: Dp = 48.dp

        /** Left and right inset of everything in the widget. */
        val HORIZONTAL_PADDING: Dp = 12.dp

        private val MIN_RING = 28.dp
        private val MAX_RING = 160.dp
        private val MAX_BUTTON = 64.dp
        private val RING_GAP = 2.dp
        private val HEIGHT_SLACK = 2.dp
        private const val RINGS = 5
        private const val FIT_STEPS = 24

        fun of(size: DpSize): WidgetLayout {
            val byWidth = ((size.width - HORIZONTAL_PADDING * 2) / RINGS - RING_GAP).coerceAtMost(MAX_RING)
            val room = size.height - HEIGHT_SLACK
            fun fits(ring: Dp) = roomy(ring).height <= room
            if (byWidth < NAMES_FROM || !fits(NAMES_FROM)) return compact(byWidth, size.height)
            var low = NAMES_FROM
            var high = byWidth
            if (fits(high)) low = high else repeat(FIT_STEPS) {
                val mid = (low + high) / 2
                if (fits(mid)) low = mid else high = mid
            }
            val spare = room - roomy(low).height
            return roomy(low, spare / 4)
        }

        /** The compact layout: the ring takes what the height leaves after the streak line and the full-width button. */
        private fun compact(byWidth: Dp, height: Dp): WidgetLayout {
            val padding = 4.dp
            val streak = StreakSizes(24.dp, 20.dp, 16.dp, 16.sp, 14.sp)
            val button = ButtonSizes(BUTTON_HEIGHT, 16.sp, 20.dp, 16.dp)
            val ring = minOf(byWidth, height - padding * 2 - streak.line - button.height - HEIGHT_SLACK).coerceIn(MIN_RING, MAX_RING)
            return WidgetLayout(ring, ring / 2, false, true, padding, 0.dp, 0.dp, 12.sp, streak, button)
        }

        /** The centred block at [ring], with [share] added to each of the four gaps around and inside it. */
        private fun roomy(ring: Dp, share: Dp = 0.dp): WidgetLayout {
            fun grow(base: Float, perRing: Float) = base + perRing * (ring.value - NAMES_FROM.value)
            return WidgetLayout(
                ring = ring,
                glyph = ring / 2,
                showNames = true,
                compact = false,
                verticalPadding = grow(8f, 1f / 6).dp + share,
                blockGap = grow(4f, 1f / 6).dp + share,
                nameBand = grow(20f, 1f / 6).dp,
                nameSize = grow(12f, 1f / 12).sp,
                streak = StreakSizes(
                    line = grow(26f, 1f / 4).dp,
                    flame = grow(22f, 1f / 4).dp,
                    snowflake = grow(18f, 1f / 6).dp,
                    text = grow(18f, 1f / 6).sp,
                    freezeText = grow(16f, 1f / 6).sp,
                ),
                button = ButtonSizes(
                    height = grow(BUTTON_HEIGHT.value, 0.15f).dp.coerceAtMost(MAX_BUTTON),
                    text = grow(16f, 1f / 16).sp,
                    icon = grow(20f, 1f / 8).dp,
                    horizontalPadding = grow(24f, 0.2f).dp,
                ),
            )
        }
    }
}
