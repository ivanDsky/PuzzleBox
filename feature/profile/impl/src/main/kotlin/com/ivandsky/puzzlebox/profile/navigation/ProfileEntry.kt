package com.ivandsky.puzzlebox.profile.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.app.screens.ProfileRoute
import com.ivandsky.puzzlebox.profile.api.LinkedInLoginKey
import com.ivandsky.puzzlebox.profile.api.ProfileKey
import com.ivandsky.puzzlebox.profile.api.SettingsKey
import com.ivandsky.puzzlebox.profile.linkedin.LinkedInLoginRoute
import com.ivandsky.puzzlebox.profile.settings.SettingsRoute

/**
 * Profile's route (pushed on the current tab) and the pages it opens. [onBack] is the back
 * icon of Profile and Settings and closes the LinkedIn login; [onOpenSettings] is Profile's gear.
 * Settings uses [onOpenCatalog], the design-system catalog, an app-owned deep page.
 * [onOpenLinkedInLogin] opens the LinkedIn login; null hides Profile's LinkedIn row.
 */
fun EntryProviderScope<NavKey>.profileEntry(
    onOpenCatalog: () -> Unit,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLinkedInLogin: (() -> Unit)? = null,
) {
    entry<ProfileKey> {
        ProfileRoute(onBack = onBack, onOpenSettings = onOpenSettings, onConnectLinkedIn = onOpenLinkedInLogin)
    }
    entry<SettingsKey> {
        SettingsRoute(onBack = onBack, onOpenCatalog = onOpenCatalog)
    }
    entry<LinkedInLoginKey> {
        LinkedInLoginRoute(onDone = onBack)
    }
}
