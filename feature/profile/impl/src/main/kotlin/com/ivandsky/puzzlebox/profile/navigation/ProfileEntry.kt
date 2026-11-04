package com.ivandsky.puzzlebox.profile.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.app.screens.ProfileRoute
import com.ivandsky.puzzlebox.profile.api.ProfileKey

/**
 * Profile's route (tab root). Its one outbound move is the design-system catalog, an
 * app-owned deep page, so the shell supplies [onOpenCatalog] rather than a key.
 */
fun EntryProviderScope<NavKey>.profileEntry(onOpenCatalog: () -> Unit) {
    entry<ProfileKey> {
        ProfileRoute(onOpenCatalog = onOpenCatalog)
    }
}
