package com.ivandsky.puzzlebox.app.debug.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.ivandsky.puzzlebox.app.debug.RadioOption
import com.ivandsky.puzzlebox.designsystem.AccentSet
import com.ivandsky.puzzlebox.designsystem.DesignVariants
import com.ivandsky.puzzlebox.designsystem.DesignVariantsStore
import com.ivandsky.puzzlebox.designsystem.Spacing

// The sweep lengths on trial (spec A7): the shipped 480 ms and one step either side.
private val SweepOptions = listOf(320, 480, 640)

/**
 * Developer design settings (spec A7): one radio row per open visual choice, built from
 * the catalog browser's compact radio options. A pick updates [DesignVariantsStore]
 * (every PuzzleBoxTheme re-themes live) and is saved to [DesignVariantsPrefs] so it
 * survives a restart. Debug only: release compiles none of this.
 */
@Composable
internal fun DesignSettingsPanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember(context) { DesignVariantsPrefs(context) }
    val variants = DesignVariantsStore.current
    val choose = { next: DesignVariants ->
        DesignVariantsStore.current = next
        prefs.save(next)
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        VariantRow(
            title = "Accent colours",
            options = AccentSet.entries,
            selected = variants.accentSet,
            label = { it.name },
            onSelect = { choose(variants.copy(accentSet = it)) },
        )
        VariantRow(
            title = "Game glyphs",
            options = listOf(false, true),
            selected = variants.glyphSolid,
            label = { solid -> if (solid) "Solid" else "Outlined" },
            onSelect = { choose(variants.copy(glyphSolid = it)) },
        )
        VariantRow(
            title = "Solve sweep",
            options = SweepOptions,
            selected = variants.sweepMs,
            label = { ms -> "$ms ms" },
            onSelect = { choose(variants.copy(sweepMs = it)) },
        )
        Text(
            "For now, only the catalog shows these changes.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun <T> VariantRow(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            options.forEach { option ->
                RadioOption(
                    label = label(option),
                    selected = option == selected,
                    onSelect = { onSelect(option) },
                )
            }
        }
    }
}
