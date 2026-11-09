package com.ivandsky.puzzlebox.profile.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.app.screens.ProfileRoute
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.profile.api.ProfileKey
import com.ivandsky.puzzlebox.profile.api.SettingsKey
import com.ivandsky.puzzlebox.profile.settings.SettingsRoute

/**
 * Profile's route (tab root). A game row opens History's timeline ([onOpenHistory], N3)
 * and the debug catalog link opens the design-system catalog, an app-owned deep page;
 * the shell supplies both. Settings ([SettingsKey]) is a shell screen: [onBack] pops it and
 * it reuses the same [onOpenCatalog].
 */
fun EntryProviderScope<NavKey>.profileEntry(
    onOpenHistory: (GameId) -> Unit,
    onOpenCatalog: () -> Unit,
    onBack: () -> Unit,
) {
    entry<ProfileKey> {
        ProfileRoute(onOpenHistory = onOpenHistory, onOpenCatalog = onOpenCatalog)
    }
    entry<SettingsKey> {
        SettingsRoute(onBack = onBack, onOpenCatalog = onOpenCatalog)
    }
}
