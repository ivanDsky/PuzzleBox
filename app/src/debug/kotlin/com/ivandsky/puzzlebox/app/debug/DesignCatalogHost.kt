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

private val CatalogTabs = listOf("Components", "Design settings")

/**
 * The debug Catalog route's root (spec A7): a two-tab row over the Showkase browser and
 * the developer design settings, so the settings need no navigation change. Each tab
 * keeps its saveable state (the browser's query, open component) while the other shows.
 */
@Composable
internal fun DesignCatalogHost() {
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
                if (selectedTab == 0) ShowkaseBrowserScreen() else DesignSettingsPanel()
            }
        }
    }
}
