package com.ivandsky.puzzlebox.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity

/**
 * Every bottom sheet in the app. Material's sheet settles with whatever fling velocity a scrolling
 * body leaves unused, so a list flung into its end kicks the idle sheet up or down. Here such a
 * fling stops at the list; only a drag aimed at the sheet itself reaches its settle. The sheet's window
 * brings its own platform haptics, so the app's are carried in: a buzz in the body still meets the Vibration gate.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    sheetMaxWidth: Dp = BottomSheetDefaults.SheetMaxWidth,
    sheetGesturesEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        sheetMaxWidth = sheetMaxWidth,
        sheetGesturesEnabled = sheetGesturesEnabled,
    ) {
        CompositionLocalProvider(LocalHapticFeedback provides haptics) {
            Column(Modifier.fillMaxWidth().nestedScroll(SwallowScrolledFling), content = content)
        }
    }
}

/** Runs between the body and the sheet: a fling something below already scrolled with ends here. */
private object SwallowScrolledFling : NestedScrollConnection {
    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
        if (consumed == Velocity.Zero) Velocity.Zero else available
}

@OptIn(ExperimentalMaterial3Api::class)
@PuzzleBoxPreviews
@Composable
private fun AppModalBottomSheetPreview() {
    PuzzleBoxTheme {
        AppModalBottomSheet(onDismissRequest = {}) {
            Text("Sheet body", modifier = Modifier.padding(Spacing.lg))
        }
    }
}
