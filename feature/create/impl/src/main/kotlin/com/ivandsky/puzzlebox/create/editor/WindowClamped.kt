package com.ivandsky.puzzlebox.create.editor

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.PopupPositionProvider

/**
 * A popup placed by [place] and then pushed back inside the window, [margin] px from its edges.
 * A popup wider or taller than the window minus the margins starts at the margin.
 */
internal class WindowClamped(
    private val margin: Int = 0,
    private val place: (anchor: IntRect, popup: IntSize, direction: LayoutDirection) -> IntOffset,
) : PopupPositionProvider {

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val wanted = place(anchorBounds, popupContentSize, layoutDirection)
        return IntOffset(
            wanted.x.coerceIn(margin, maxOf(margin, windowSize.width - margin - popupContentSize.width)),
            wanted.y.coerceIn(margin, maxOf(margin, windowSize.height - margin - popupContentSize.height)),
        )
    }

    companion object {
        /** Above the anchor with a [gap], centred on it across. */
        fun above(gap: Int): WindowClamped = WindowClamped { anchor, popup, _ ->
            IntOffset(anchor.left + (anchor.width - popup.width) / 2, anchor.top - gap - popup.height)
        }

        /** Over the anchor, its top end corner on the anchor's, [margin] px clear of the window's edges. */
        fun overTopEnd(margin: Int): WindowClamped = WindowClamped(margin) { anchor, popup, direction ->
            val x = if (direction == LayoutDirection.Ltr) anchor.right - popup.width else anchor.left
            IntOffset(x, anchor.top)
        }
    }
}
