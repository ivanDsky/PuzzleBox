package com.ivandsky.puzzlebox.train

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.Spacing

/** The phone's Train page: the pager page's side inset and top gap. */
@Composable
internal fun GoldenPage(content: @Composable () -> Unit) =
    Box(Modifier.fillMaxSize().padding(horizontal = Spacing.lg).padding(top = Spacing.sm)) { content() }

/** The tablet's two panes as the Library lays them: the list column, a gap, then the right pane. */
@Composable
internal fun GoldenBeside(open: Boolean, pane: @Composable () -> Unit) = Row(
    Modifier.fillMaxSize().padding(start = Spacing.xl, top = Spacing.lg, end = Spacing.xxl, bottom = Spacing.xl),
) {
    Box(Modifier.width(400.dp).padding(horizontal = 6.dp).padding(top = Spacing.sm)) { GoldenTechniqueList(open) }
    Spacer(Modifier.width(Spacing.xl))
    Box(Modifier.weight(1f)) { pane() }
}

/** The tablet's right pane while no technique is open. */
@Composable
internal fun GoldenPlaceholder() = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Text("Pick a technique", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
