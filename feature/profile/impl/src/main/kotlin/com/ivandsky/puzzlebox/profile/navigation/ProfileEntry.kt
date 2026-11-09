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
 * the shell supplies both. [slim] draws identity and sign-in only, and a non-null
 * [onProfileBack] draws Profile's back icon; the defaults are today's screen. Settings
 * ([SettingsKey]) is a shell screen: [onBack] pops it and it reuses the same [onOpenCatalog].
 */
fun EntryProviderScope<NavKey>.profileEntry(
    onOpenHistory: (GameId) -> Unit,
    onOpenCatalog: () -> Unit,
    onBack: () -> Unit,
    slim: Boolean = false,
    onProfileBack: (() -> Unit)? = null,
) {
    entry<ProfileKey> {
        ProfileRoute(
            onOpenHistory = onOpenHistory,
            onOpenCatalog = onOpenCatalog,
            slim = slim,
            onBack = onProfileBack,
        )
    }
    entry<SettingsKey> {
        SettingsRoute(onBack = onBack, onOpenCatalog = onOpenCatalog)
    }
}
