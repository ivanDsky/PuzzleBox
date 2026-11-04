package com.ivandsky.puzzlebox.alpha

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

// Calls a composable that lives in games:beta; that does not cover it, previews count per module.
@Preview
@Composable
private fun CrossModulePreview() {
    OnlyPreviewedInAlpha()
}
