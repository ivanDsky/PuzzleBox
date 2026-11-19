package com.ivandsky.puzzlebox.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.airbnb.android.showkase.annotation.ShowkaseComposable

/** [AppPopup] placed at [offset] from its parent's top-left corner, with the Vibration gate re-provided inside. */
@Composable
fun AppOffsetPopup(
    offset: IntOffset,
    properties: PopupProperties = PopupProperties(),
    content: @Composable () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Popup(offset = offset, properties = properties) {
        CompositionLocalProvider(LocalHapticFeedback provides haptics, content = content)
    }
}

// Previews — Studio tooling only.

@ShowkaseComposable(name = "AppOffsetPopup", group = "Menus")
@PuzzleBoxPreviews
@Composable
internal fun AppOffsetPopupPreview() {
    PuzzleBoxTheme {
        Box(Modifier.size(Spacing.xxl * 3)) {
            AppOffsetPopup(offset = IntOffset.Zero) {
                Box(Modifier.size(Spacing.xxl))
            }
        }
    }
}
