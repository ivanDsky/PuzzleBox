package com.ivandsky.puzzlebox.designsystem

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview

/**
 * The module's one light/dark multipreview: declared HERE (not the binary androidx
 * @PreviewLightDark) because Showkase 1.0.5 only expands multipreview annotation
 * classes it can see in the processed sources — a jar-packaged annotation is invisible
 * to its round scan and indexes nothing (ticket 00, AGP9/KSP2 findings). Shared by
 * every design-system preview. Lives in its own file so no component file owns the
 * module's preview infrastructure.
 */
@Preview(name = "Light", uiMode = Configuration.UI_MODE_NIGHT_NO, showBackground = true)
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
annotation class PuzzleBoxPreviews
