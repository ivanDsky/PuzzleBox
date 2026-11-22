package com.ivandsky.puzzlebox.play.community.shelves.seeall

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import com.ivandsky.puzzlebox.designsystem.SecondaryButton
import com.ivandsky.puzzlebox.designsystem.Spacing

/** "Nothing matches “q”", a line on what to do, and a Clear filters button. With no word typed the filters are what matched nothing. */
@Composable
internal fun NoMatchState(query: String, onClearFilters: () -> Unit, modifier: Modifier = Modifier) {
    val word = query.trim()
    Column(
        modifier = modifier.fillMaxWidth().padding(Spacing.xl).testTag("seeall.nomatch"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            if (word.isEmpty()) "Nothing matches these filters" else "Nothing matches “$word”",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            "Try another word, or clear the filters.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        SecondaryButton("Clear filters", onClick = onClearFilters, modifier = Modifier.testTag("seeall.nomatch.clear"))
    }
}
