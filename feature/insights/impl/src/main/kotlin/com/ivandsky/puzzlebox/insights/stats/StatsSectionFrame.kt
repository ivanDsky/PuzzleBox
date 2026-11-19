package com.ivandsky.puzzlebox.insights.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.insights.domain.stats.SectionState

/**
 * One stats section's frame: its [title] over what its [state] shows: a quiet line while it loads or when it cannot load
 * right now (A16), and [content] once it is ready. [tag] names the section for tests.
 */
@Composable
internal fun <T> StatsSectionFrame(
    title: String,
    state: SectionState<T>,
    tag: String,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    Column(modifier.fillMaxWidth().testTag(tag), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(vertical = Spacing.xs).semantics { heading() },
        )
        when (state) {
            SectionState.Loading -> StatsSectionNote("Loading…", "$tag.loading")
            SectionState.Unavailable -> StatsSectionNote("Can't load right now", "$tag.unavailable")
            is SectionState.Ready -> content(state.value)
        }
    }
}

@Composable
private fun StatsSectionNote(text: String, tag: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag(tag),
    )
}
