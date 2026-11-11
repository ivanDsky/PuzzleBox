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
import androidx.compose.material3.HorizontalDivider
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
 * Settings, a pushed tool screen: a compact top bar with Back, then Appearance, Games, About (the version name), and — in debug builds only — a "Debug build only" block set off by a divider (only when [onOpenCatalog] is given): the [debugSections] slot (the app's flags and game tools), then the Component catalog row.
 */
@Composable
internal fun SettingsScreen(
    state: SettingsUiState,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetQueensAutoCross: (Boolean) -> Unit,
    onSetSudokuPeerHighlight: (Boolean) -> Unit,
    onSetSudokuSameDigitHighlight: (Boolean) -> Unit,
    onBack: () -> Unit,
    onOpenCatalog: (() -> Unit)?,
    modifier: Modifier = Modifier,
    debugSections: @Composable () -> Unit = {},
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
            GamesSection(
                state = state.games,
                onQueensAutoCross = onSetQueensAutoCross,
                onSudokuPeerHighlight = onSetSudokuPeerHighlight,
                onSudokuSameDigitHighlight = onSetSudokuSameDigitHighlight,
            )
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
                // Everything below the divider exists only in debug builds.
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(
                    modifier = Modifier.fillMaxWidth().testTag("settings.debug"),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xl),
                ) {
                    Text(
                        text = "Debug build only",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    debugSections()
                    Column(
                        modifier = Modifier.fillMaxWidth().testTag("settings.developer"),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        SectionTitle("Component catalog")
                        SecondaryButton(
                            text = "Open component browser",
                            onClick = onOpenCatalog,
                            modifier = Modifier.fillMaxWidth().testTag("settings.catalog"),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun SectionTitle(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
}

@PuzzleBoxPreviews
@Composable
internal fun SettingsScreenPreview() {
    PuzzleBoxTheme {
        SettingsScreen(
            state = SettingsUiState(themeMode = ThemeMode.DARK, versionName = "1.4.0"),
            onSetThemeMode = {},
            onSetQueensAutoCross = {},
            onSetSudokuPeerHighlight = {},
            onSetSudokuSameDigitHighlight = {},
            onBack = {},
            onOpenCatalog = {},
        )
    }
}
