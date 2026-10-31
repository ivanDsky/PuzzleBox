package com.ivandsky.puzzlebox.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.CompactTopBar
import com.ivandsky.puzzlebox.designsystem.EmptyState
import com.ivandsky.puzzlebox.designsystem.Spacing

/**
 * The history browser (A8): the days the app has captured, newest first. Phones — and
 * portrait of any width — see one pane; expanded landscape gets list-detail (07).
 */
@Composable
fun HistoryScreen(
    layout: HistoryLayout,
    onBack: () -> Unit,
    rows: List<HistoryDayRow>,
    modifier: Modifier = Modifier,
) {
    // Cards float ABOVE the pane in both themes (home convention): white on cool paper
    // in light, lighter slate on near-black in dark — the pane/card pairing flips with
    // the resolved scheme so it stays correct when the user forces the other appearance.
    val scheme = MaterialTheme.colorScheme
    val cardsFloatLighter = scheme.surface.luminance() > scheme.surfaceContainerHighest.luminance()
    val paneColor = if (cardsFloatLighter) scheme.surfaceContainer else scheme.surface
    val cardColor = if (cardsFloatLighter) scheme.surface else scheme.surfaceContainerLow

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(paneColor),
    ) {
        ListPane(
            rows = rows,
            onBack = onBack,
            cardColor = cardColor,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun ListPane(
    rows: List<HistoryDayRow>,
    onBack: () -> Unit,
    cardColor: Color,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("history.list"),
    ) {
        item(key = "top") {
            CompactTopBar(
                title = "History",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        }
        if (rows.isEmpty()) {
            item(key = "empty") {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg, vertical = Spacing.xl),
                    shape = MaterialTheme.shapes.large,
                    color = cardColor,
                ) {
                    EmptyState(
                        title = "Nothing captured yet",
                        message = "Days you play are saved on this device — replay any of them, even offline.",
                        modifier = Modifier
                            .testTag("history.empty"),
                    )
                }
            }
        } else {
            items(
                items = rows,
                key = { it.day.toEpochDay() },
                contentType = { "row" },
            ) { row ->
                HistoryRowView(
                    row = row,
                    won = false,
                    onClick = {},
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                )
            }
        }
    }
}

@Composable
private fun HistoryRowView(
    row: HistoryDayRow,
    won: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .testTag("history.row.${row.day}"),
        shape = MaterialTheme.shapes.small,
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = row.headline,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = row.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (won) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Record set",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
