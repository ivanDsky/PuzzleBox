package com.ivandsky.puzzlebox.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private val PagerTabs = listOf(
    UnderlineTab("New today", "preview.pager.tab.0", count = 8),
    UnderlineTab("Friends", "preview.pager.tab.1", count = 5),
    UnderlineTab("Yours", "preview.pager.tab.2"),
)

private val PagerSampleModifier = Modifier.fillMaxWidth().height(160.dp)

/** A page that only names itself. */
@Composable
private fun SamplePage(page: Int) {
    Box(Modifier.fillMaxWidth().padding(Spacing.lg), contentAlignment = Alignment.TopStart) {
        Text("${PagerTabs[page].label} page", style = MaterialTheme.typography.titleMedium)
    }
}

/** The pager with [initialPage] selected, for the goldens. */
@Composable
internal fun TabPagerSample(initialPage: Int) {
    UnderlineTabPager(PagerTabs, onReselect = {}, modifier = PagerSampleModifier, initialPage = initialPage) { SamplePage(it) }
}

@PuzzleBoxPreviews
@Composable
internal fun UnderlineTabPagerPageOnePreview() {
    PuzzleBoxTheme {
        Surface { UnderlineTabPager(PagerTabs, onReselect = {}, modifier = PagerSampleModifier, initialPage = 0) { SamplePage(it) } }
    }
}

@PuzzleBoxPreviews
@Composable
internal fun UnderlineTabPagerPageTwoPreview() {
    PuzzleBoxTheme {
        Surface { UnderlineTabPager(PagerTabs, onReselect = {}, modifier = PagerSampleModifier, initialPage = 1) { SamplePage(it) } }
    }
}

@PuzzleBoxPreviews
@Composable
internal fun TabFadePanePreview() {
    PuzzleBoxTheme {
        Surface {
            val link = remember { TabFadeLink(1) }
            Row(PagerSampleModifier) {
                UnderlineTabPager(PagerTabs, onReselect = {}, modifier = Modifier.width(220.dp), initialPage = 1, link = link) { SamplePage(it) }
                TabFadePane(link, Modifier.weight(1f)) { SamplePage(it) }
            }
        }
    }
}
