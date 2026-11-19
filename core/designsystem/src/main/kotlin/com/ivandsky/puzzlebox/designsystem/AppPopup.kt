package com.ivandsky.puzzlebox.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.airbnb.android.showkase.annotation.ShowkaseComposable

/**
 * The app's popup window. A popup brings its own platform haptics, so the outer [LocalHapticFeedback] (the Vibration
 * gate) is re-provided inside it; a buzz played in the popup still honours the Vibration setting.
 */
@Composable
fun AppPopup(
    popupPositionProvider: PopupPositionProvider,
    onDismissRequest: (() -> Unit)? = null,
    properties: PopupProperties = PopupProperties(),
    content: @Composable () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Popup(popupPositionProvider, onDismissRequest, properties) {
        CompositionLocalProvider(LocalHapticFeedback provides haptics, content = content)
    }
}

// Previews — Studio tooling only.

@ShowkaseComposable(name = "AppPopup", group = "Menus")
@PuzzleBoxPreviews
@Composable
internal fun AppPopupPreview() {
    PuzzleBoxTheme {
        Box(Modifier.size(Spacing.xxl * 3)) {
            AppPopup(
                popupPositionProvider = object : PopupPositionProvider {
                    override fun calculatePosition(
                        anchorBounds: IntRect,
                        windowSize: IntSize,
                        layoutDirection: LayoutDirection,
                        popupContentSize: IntSize,
                    ) = anchorBounds.topLeft
                },
            ) {
                Box(Modifier.size(Spacing.xxl))
            }
        }
    }
}
