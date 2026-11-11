package com.ivandsky.puzzlebox.profile.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.ivandsky.puzzlebox.designsystem.CompactTopBar
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.SecondaryButton
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.model.ThemeMode
import com.ivandsky.puzzlebox.profile.AppearanceRow

/**
 * Settings, a pushed tool screen: a compact top bar with Back, then Appearance, LinkedIn (only when
 * [onConnectLinkedIn] is given), About (the version name), and — only when [onOpenCatalog] is given (debug builds) — Developer.
 */
@Composable
internal fun SettingsScreen(
    state: SettingsUiState,
    onSetThemeMode: (ThemeMode) -> Unit,
    onBack: () -> Unit,
    onOpenCatalog: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onConnectLinkedIn: (() -> Unit)? = null,
    onResumeLinkedIn: () -> Unit = {},
    onDisconnectLinkedIn: () -> Unit = {},
) {
    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        CompactTopBar(
            title = "Settings",
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.testTag("settings.back")) {
                    Icon(
                        imageVector = PuzzleIcons.Back,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.xl, vertical = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
        ) {
            Column(Modifier.testTag("settings.appearance")) {
                AppearanceRow(current = state.themeMode, onSelect = onSetThemeMode)
            }
            if (onConnectLinkedIn != null) {
                Column(Modifier.testTag("settings.linkedin.section")) {
                    LinkedInSection(
                        state = state.linkedIn,
                        onConnect = onConnectLinkedIn,
                        onResume = onResumeLinkedIn,
                        onDisconnect = onDisconnectLinkedIn,
                    )
                }
            }
            Column(
                modifier = Modifier.fillMaxWidth().testTag("settings.about"),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                SectionTitle("About")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Version",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = state.versionName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (onOpenCatalog != null) {
                Column(
                    modifier = Modifier.fillMaxWidth().testTag("settings.developer"),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    SectionTitle("Developer")
                    SecondaryButton(
                        text = "Design system catalog",
                        onClick = onOpenCatalog,
                        modifier = Modifier.fillMaxWidth().testTag("settings.catalog"),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
}

@PuzzleBoxPreviews
@Composable
internal fun SettingsScreenPreview() {
    PuzzleBoxTheme {
        SettingsScreen(
            state = SettingsUiState(themeMode = ThemeMode.DARK, versionName = "1.4.0"),
            onSetThemeMode = {},
            onBack = {},
            onOpenCatalog = {},
            onConnectLinkedIn = {},
        )
    }
}
