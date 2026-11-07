package com.ivandsky.puzzlebox.app.debug

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.app.debug.design.DesignSettingsPanel
import com.ivandsky.puzzlebox.app.debug.flags.ReleaseFlagsPanel

private val CatalogTabs = listOf("Components", "Design settings", "Release flags")

/**
 * The debug Catalog route's root (spec A7): a tab row over the Showkase browser, the
 * developer design settings and the release-flag overrides (ADR-0022), so none of them
 * needs a navigation change. Each tab keeps its saveable state (the browser's query, open
 * component) while another shows.
 */
@Composable
internal fun DesignCatalogHost(openGame: (String) -> Unit) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabStates = rememberSaveableStateHolder()
    Column(Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = selectedTab, modifier = Modifier.fillMaxWidth()) {
            CatalogTabs.forEachIndexed { index, title ->
                Tab(
                    selected = index == selectedTab,
                    onClick = { selectedTab = index },
                    text = { Text(title) },
                )
            }
        }
        Box(Modifier.weight(1f)) {
            tabStates.SaveableStateProvider(selectedTab) {
                when (selectedTab) {
                    0 -> ShowkaseBrowserScreen()
                    1 -> DesignSettingsPanel(openGame)
                    else -> ReleaseFlagsPanel()
                }
            }
        }
    }
}
