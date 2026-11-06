package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.gameui.PlayLayout

/**
 * One game's page (06-a §Game page): the name over the archive's span and solved count,
 * then the [dayCard], the [calendar], the stats and how to play. [PlayLayout.TwoPane]
 * lays them in two columns that fill the pane and never scroll (left: day card over the
 * calendar; right: stats over how to play). [PlayLayout.SinglePane] stacks them in one
 * scrolling column under a back button ([onBack]; null hides it). [offline] adds the
 * offline pill; [hasLesson] false hides "Open the lesson" ([onOpenLesson]). [scroll] is
 * the phone column's, so picking a day can bring the day card back into view.
 */
@Composable
internal fun GamePage(
    row: LibraryRowModel,
    layout: PlayLayout,
    dayCard: @Composable () -> Unit,
    calendar: @Composable () -> Unit,
    onBack: (() -> Unit)?,
    onOpenLesson: () -> Unit,
    modifier: Modifier = Modifier,
    offline: Boolean = false,
    hasLesson: Boolean = true,
    scroll: ScrollState = rememberScrollState(),
) {
    val reduced = rememberReducedMotion()
    val tag = "library.page.${row.card.game.value}"
    if (layout == PlayLayout.TwoPane) {
        Column(modifier.fillMaxSize().testTag(tag), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
                PageTitle(row, Modifier.weight(1f))
                if (offline) OfflinePill()
            }
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                Column(Modifier.weight(1.3f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                    EnterFade(0, reduced) { dayCard() }
                    EnterFade(1, reduced, Modifier.weight(1f).fillMaxWidth()) { calendar() }
                }
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                    EnterFade(2, reduced) { GameStatsCard(row) }
                    EnterFade(3, reduced, Modifier.weight(1f).fillMaxWidth()) {
                        HowToPlayCard(row, hasLesson, onOpenLesson, Modifier.fillMaxHeight(), fill = true)
                    }
                }
            }
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .testTag(tag)
                .verticalScroll(scroll)
                .padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("library.page.back")) {
                        Icon(PuzzleIcons.Back, contentDescription = "Back")
                    }
                }
                PageTitle(row, Modifier.weight(1f))
            }
            if (offline) OfflinePill()
            EnterFade(0, reduced) { dayCard() }
            EnterFade(1, reduced) { calendar() }
            EnterFade(2, reduced) { GameStatsCard(row) }
            EnterFade(3, reduced) { HowToPlayCard(row, hasLesson, onOpenLesson) }
        }
    }
}

@Composable
private fun PageTitle(row: LibraryRowModel, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(row.card.name, style = MaterialTheme.typography.headlineLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        row.progress?.let { archive ->
            val puzzles = if (archive.total == 1) "1 puzzle" else "${count(archive.total)} puzzles"
            Text(
                "$puzzles since ${longDate(archive.range.start)} · ${count(archive.solved)} of ${count(archive.total)} solved",
                style = MaterialTheme.typography.bodyMedium.tabular(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } ?: Box(Modifier.height(20.dp))
    }
}
