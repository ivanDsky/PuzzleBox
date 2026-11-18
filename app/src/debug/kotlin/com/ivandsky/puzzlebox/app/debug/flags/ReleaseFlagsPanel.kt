package com.ivandsky.puzzlebox.app.debug.flags

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.common.flags.ReleaseFlagKey
import com.ivandsky.puzzlebox.designsystem.Spacing
import org.koin.compose.koinInject

/**
 * Settings' Release flags section (ADR-0022): one switch per declared flag, in [DebugReleaseFlags.declared]'s
 * order. A switch forces the flag in this debug build and every collecting screen updates live; Reset drops
 * the override back to the flag's release default. Debug only: release compiles none of this.
 */
@Composable
internal fun ReleaseFlagsPanel(
    modifier: Modifier = Modifier,
    flags: DebugReleaseFlags = koinInject(),
) {
    val overrides by flags.overrides.collectAsStateWithLifecycle()
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        if (flags.declared.isEmpty()) {
            Text(
                "No release flags in flight.",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "A trunk feature's first ticket adds its flag to its module's flag enum (ADR-0022).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        flags.declared.forEach { flag ->
            FlagRow(
                flag = flag,
                override = overrides[flag],
                onSet = { value -> flags.setOverride(flag, value) },
            )
        }
    }
}

@Composable
private fun FlagRow(flag: ReleaseFlagKey, override: Boolean?, onSet: (Boolean?) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().testTag("debug.flag.${flag.key}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Column(Modifier.weight(1f)) {
            Text(flag.label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            val release = if (flag.releaseDefault) "on" else "off"
            Text(
                if (override == null) "${flag.key} · release: $release" else "${flag.key} · release: $release · forced",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (override != null) {
            TextButton(onClick = { onSet(null) }) { Text("Reset") }
        }
        Switch(checked = override ?: flag.releaseDefault, onCheckedChange = { onSet(it) })
    }
}
