package com.ivandsky.puzzlebox.designsystem

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val BadgeSize = 18.dp

private val ThreeTabs = listOf(
    UnderlineTab("New today", "preview.tab.0", count = 8),
    UnderlineTab("Friends", "preview.tab.1", count = 5),
    UnderlineTab("Yours", "preview.tab.2", count = 120),
)

private val FourTabs = listOf(
    UnderlineTab("Dailies", "preview.tab.0"),
    UnderlineTab("Friends", "preview.tab.1", count = 12),
    UnderlineTab("Duels", "preview.tab.2", description = "Duels, 2 need you", trailing = { PreviewBadge("2") }),
    UnderlineTab("Custom", "preview.tab.3", count = 3),
)

/** The strip stacked once per selected position, so one image shows the bar under every tab. */
@Composable
private fun EveryPosition(tabs: List<UnderlineTab>) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        tabs.indices.forEach { selected -> UnderlineTabs(tabs, selected, onSelect = {}) }
    }
}

@Composable
internal fun ThreePlainTabsSample() = EveryPosition(ThreeTabs.map { it.copy(count = null) })

@Composable
internal fun ThreeCountsTabsSample() = EveryPosition(ThreeTabs)

@Composable
internal fun FourPlainTabsSample() = EveryPosition(FourTabs.map { it.copy(count = null, description = null, trailing = null) })

@Composable
internal fun FourBadgeTabsSample() = EveryPosition(FourTabs)

/** Stand-in for the Games tab's red need-you badge, which lives with its caller. */
@Composable
private fun PreviewBadge(text: String) {
    Box(
        Modifier
            .widthIn(min = BadgeSize)
            .heightIn(min = BadgeSize)
            .clip(PillShape)
            .background(MaterialTheme.colorScheme.error)
            .padding(horizontal = Spacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold).tabular(),
            color = MaterialTheme.colorScheme.onError,
        )
    }
}

@PuzzleBoxPreviews
@Composable
internal fun UnderlineTabsThreePreview() {
    PuzzleBoxTheme { Surface { UnderlineTabs(ThreeTabs, selected = 1, onSelect = {}) } }
}

@PuzzleBoxPreviews
@Composable
internal fun UnderlineTabsFourPreview() {
    PuzzleBoxTheme { Surface { FourBadgeTabsSample() } }
}

@PuzzleBoxPreviews
@Composable
internal fun UnderlineTabsFourPlainPreview() {
    PuzzleBoxTheme { Surface { FourPlainTabsSample() } }
}

@Preview(name = "320dp 1.3 Light", widthDp = 320, fontScale = 1.3f, showBackground = true)
@Preview(name = "320dp 1.3 Dark", widthDp = 320, fontScale = 1.3f, uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
internal fun UnderlineTabsFourLargeTextPreview() {
    PuzzleBoxTheme { Surface { FourBadgeTabsSample() } }
}

@PuzzleBoxPreviews
@Composable
internal fun UnderlineTabsAtPreview() {
    PuzzleBoxTheme { Surface { UnderlineTabsAt(ThreeTabs, position = { 0.6f }, onSelect = {}) } }
}
