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
 * Profile's route (pushed on the current tab). [onProfileBack] is its back icon and
 * [onOpenSettings] its gear. Settings ([SettingsKey]) is a shell screen: [onBack] pops it and
 * it uses [onOpenCatalog], the design-system catalog, an app-owned deep page. [onOpenLinkedInLogin]
 * opens the LinkedIn login; null hides Settings' LinkedIn section.
 */
fun EntryProviderScope<NavKey>.profileEntry(
    onOpenCatalog: () -> Unit,
    onBack: () -> Unit,
    onProfileBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLinkedInLogin: (() -> Unit)? = null,
) {
    entry<ProfileKey> {
        ProfileRoute(onBack = onProfileBack, onOpenSettings = onOpenSettings)
    }
    entry<SettingsKey> {
        SettingsRoute(onBack = onBack, onOpenCatalog = onOpenCatalog, onConnectLinkedIn = onOpenLinkedInLogin)
    }
    entry<LinkedInLoginKey> {
        LinkedInLoginRoute(onDone = onBack)
    }
}
