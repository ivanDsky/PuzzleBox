package com.ivandsky.puzzlebox.designsystem

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview

/** Light and dark in one annotation so every preview covers both themes. */
@Preview(name = "Light", uiMode = Configuration.UI_MODE_NIGHT_NO, showBackground = true)
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
annotation class PuzzleBoxPreviews
