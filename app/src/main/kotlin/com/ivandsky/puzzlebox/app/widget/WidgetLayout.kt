package com.ivandsky.puzzlebox.app.widget

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Every size the widget draws with, worked out from the size the launcher gave it ([of]), so the widget fills 4 by 2 cells up to a
 * full tablet page. Rings grow with the space; text and streak icons step up with them. At [compact] (4 by 2, and any size whose
 * rings stay under [NAMES_FROM]) the streak line sits top-left and the button is full width; above that the streak, rings and
 * button are one centred block, with the game names under the rings and a pill button that hugs its words.
 */
internal data class WidgetLayout(
    val ring: Dp,
    val glyph: Dp,
    val showNames: Boolean,
    val compact: Boolean,
    val verticalPadding: Dp,
    val blockGap: Dp,
    val nameSize: TextUnit,
    val streak: StreakSizes,
) {
    class StreakSizes(val line: Dp, val flame: Dp, val snowflake: Dp, val text: TextUnit, val freezeText: TextUnit)

    /** One step of the scale: what is drawn around the rings, and the smallest ring it is drawn for. */
    private enum class Tier(
        val minRing: Dp,
        val verticalPadding: Dp,
        val blockGap: Dp,
        val nameBand: Dp,
        val nameSize: TextUnit,
        val streak: StreakSizes,
    ) {
        Compact(MIN_RING, 4.dp, 0.dp, 0.dp, 12.sp, StreakSizes(24.dp, 20.dp, 16.dp, 16.sp, 14.sp)),
        Roomy(NAMES_FROM, 8.dp, 4.dp, 20.dp, 12.sp, StreakSizes(26.dp, 22.dp, 18.dp, 18.sp, 16.sp)),
        Spacious(80.dp, 12.dp, 8.dp, 24.dp, 14.sp, StreakSizes(32.dp, 28.dp, 22.dp, 22.sp, 20.sp));

        /** The height this tier spends on everything but the rings, for a launcher that rounds the reported height a hair down. */
        val chrome: Dp get() = verticalPadding * 2 + streak.line + blockGap * 2 + BUTTON_HEIGHT + nameBand + HEIGHT_SLACK
    }

    companion object {
        /** The ring size at which a game's name is drawn under it: the old large layout's ring. */
        val NAMES_FROM: Dp = 56.dp

        /** The 48dp touch-target floor; also the Up next pill's height. */
        val BUTTON_HEIGHT: Dp = 48.dp

        /** Left and right inset of everything in the widget. */
        val HORIZONTAL_PADDING: Dp = 12.dp

        private val MIN_RING = 28.dp
        private val MAX_RING = 96.dp
        private val RING_GAP = 2.dp
        private val HEIGHT_SLACK = 2.dp
        private const val RINGS = 5

        fun of(size: DpSize): WidgetLayout {
            val byWidth = (size.width - HORIZONTAL_PADDING * 2) / RINGS - RING_GAP
            fun ringIn(tier: Tier): Dp = minOf(byWidth, size.height - tier.chrome).coerceIn(MIN_RING, MAX_RING)
            val tier = Tier.entries.asReversed().first { it == Tier.Compact || ringIn(it) >= it.minRing }
            val ring = ringIn(tier)
            return WidgetLayout(
                ring = ring,
                glyph = ring / 2,
                showNames = tier != Tier.Compact,
                compact = tier == Tier.Compact,
                verticalPadding = tier.verticalPadding,
                blockGap = tier.blockGap,
                nameSize = tier.nameSize,
                streak = tier.streak,
            )
        }
    }
}
