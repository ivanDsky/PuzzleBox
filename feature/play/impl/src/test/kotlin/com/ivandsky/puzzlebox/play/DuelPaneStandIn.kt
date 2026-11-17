package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.Spacing

/** A stand-in for the compete module's pane: the selected duel's header, puzzle cards with Start, and the footer, in the pane's frame. */
@Composable
internal fun DuelPaneStandIn(modifier: Modifier) {
    Surface(
        modifier.fillMaxSize(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.fillMaxSize().padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text("Duel with Sam", style = MaterialTheme.typography.headlineSmall)
            Text("Best of 3 · ends Tue 10:00", color = MaterialTheme.colorScheme.onSurfaceVariant)
            repeat(3) { index ->
                Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.large) {
                    Row(Modifier.padding(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
                        Text("Puzzle ${index + 1}", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        Button(onClick = {}) { Text(if (index == 0) "Resume" else "Start") }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Button(onClick = {}, Modifier.fillMaxWidth()) { Text("See results") }
        }
    }
}
