package com.ivandsky.puzzlebox.insights.stats.grouped

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview

/** The landscape tablet, light and dark: the grouped page's previews take it next to the phone's `PuzzleBoxPreviews`. */
@Preview(name = "Tablet Light", widthDp = 1280, heightDp = 800, showBackground = true)
@Preview(name = "Tablet Dark", widthDp = 1280, heightDp = 800, showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
internal annotation class StatsTabletPreviews
