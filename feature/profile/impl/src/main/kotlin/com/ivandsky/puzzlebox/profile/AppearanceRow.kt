package com.ivandsky.puzzlebox.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.SegmentedToggle
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.model.ThemeMode

/**
 * Appearance (N4, moved here from the Home gear): System / Light / Dark, one tap each
 * (tag `profile.appearance.<mode>`, lowercase). [onSelect] persists the choice.
 */
@Composable
internal fun AppearanceRow(current: ThemeMode, onSelect: (ThemeMode) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().testTag("profile.appearance"),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = "Appearance",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        SegmentedToggle(
            options = ThemeMode.entries,
            selected = current,
            onSelect = onSelect,
            label = {
                when (it) {
                    ThemeMode.SYSTEM -> "System"
                    ThemeMode.LIGHT -> "Light"
                    ThemeMode.DARK -> "Dark"
                }
            },
            tag = { "profile.appearance.${it.name.lowercase()}" },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
