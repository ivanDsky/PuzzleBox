package com.ivandsky.puzzlebox.app.debug.haptics

import android.os.Vibrator
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.SecondaryButton
import com.ivandsky.puzzlebox.designsystem.Spacing

/**
 * Settings' Haptics lab (#1686): every way the app could move the motor, one tappable row each, so a tester with no adb
 * can report the row ids they feel. It plays straight on the Vibrator and the View, past the in-app Vibration switch on
 * purpose: it measures the device, not the app. Collapsed until opened so Settings stays short. Debug only.
 */
@Composable
internal fun HapticsLabPanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val view = LocalView.current
    val vibrator = remember(context) { context.getSystemService(Vibrator::class.java) }
    var open by rememberSaveable { mutableStateOf(false) }
    var last by remember { mutableStateOf("none") }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text(
            "Plays past the in-app Vibration switch. Rows tagged touch and the View rows follow the system touch setting.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SecondaryButton(
            text = if (open) "Hide haptics lab" else "Open haptics lab",
            onClick = { open = !open },
            modifier = Modifier.testTag("debug.haptics.toggle"),
        )
        if (open) {
            val lines = remember(context, vibrator) { vibrator?.let { capabilityLines(context, it) }.orEmpty() }
            val groups = remember(vibrator, view) { vibrator?.let(::vibratorGroups).orEmpty() + platformGroup(view) }
            (lines.ifEmpty { listOf("No vibrator service") }).forEach {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
            }
            Text(
                "Last played: $last",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag("debug.haptics.last"),
            )
            groups.forEach { group ->
                LabGroup(group) { row ->
                    row.play()
                    last = row.id
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LabGroup(group: HapticsLabGroup, onPlay: (HapticsLabRow) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(group.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            group.rows.forEach { row ->
                SecondaryButton(text = row.id, onClick = { onPlay(row) }, modifier = Modifier.testTag("debug.haptics.${row.id}"))
            }
        }
    }
}
