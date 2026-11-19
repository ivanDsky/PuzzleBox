package com.ivandsky.puzzlebox.insights.stats

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/** The one plain line, with no title, that stands in a friends section's place until [text] says what unlocks it. [tag] names it for tests. */
@Composable
internal fun StatsUnlockLine(text: String, tag: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.fillMaxWidth().testTag(tag),
    )
}
