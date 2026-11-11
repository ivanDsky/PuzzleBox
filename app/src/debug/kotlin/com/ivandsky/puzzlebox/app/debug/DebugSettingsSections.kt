package com.ivandsky.puzzlebox.app.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.app.debug.design.DesignSettingsPanel
import com.ivandsky.puzzlebox.app.debug.flags.ReleaseFlagsPanel
import com.ivandsky.puzzlebox.designsystem.Spacing

/**
 * The debug tools Settings shows flat in debuggable builds: Release flags, Design variants
 * and Debug games, each under its own title like the screen's other sections. [openGame]
 * opens a game by id. Debug only: release compiles none of this.
 */
@Composable
internal fun DebugSettingsSections(openGame: (String) -> Unit) {
    DebugSection(title = "Release flags", tag = "settings.debug.flags") { ReleaseFlagsPanel() }
    DebugSection(title = "Design variants", tag = "settings.debug.design") { DesignSettingsPanel() }
    DebugSection(title = "Debug games", tag = "settings.debug.games") { DebugGamesSection(openGame) }
}

@Composable
private fun DebugSection(title: String, tag: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag(tag),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        content()
    }
}
