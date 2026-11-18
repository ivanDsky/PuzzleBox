package com.ivandsky.puzzlebox.profile.navigation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.app.screens.ProfileRoute
import com.ivandsky.puzzlebox.profile.api.LinkedInLoginKey
import com.ivandsky.puzzlebox.profile.api.ProfileKey
import com.ivandsky.puzzlebox.profile.api.SettingsKey
import com.ivandsky.puzzlebox.profile.api.WhatsNewKey
import com.ivandsky.puzzlebox.profile.linkedin.LinkedInLoginRoute
import com.ivandsky.puzzlebox.profile.settings.SettingsRoute
import com.ivandsky.puzzlebox.profile.settings.VisibilitySlot
import com.ivandsky.puzzlebox.profile.whatsnew.WhatsNewRoute

/**
 * Profile's route and the pages it opens. Where they sit is the shell's choice, made through
 * the callbacks: in the layer above the tabs, or pushed on the current tab. [onBack] is the back
 * icon of Profile and Settings and closes the LinkedIn login; [onOpenSettings] is Profile's gear.
 * [onOpenWhatsNew] is Settings' What's new row. Settings uses [onOpenCatalog], the component catalog, an app-owned deep page, and [debugSections], the app's debug tools
 * (shown only in debuggable builds).
 * [visibility] (null: none) adds Settings' Visibility section.
 * [onOpenLinkedInLogin] opens the LinkedIn login; null hides Profile's LinkedIn row.
 */
fun EntryProviderScope<NavKey>.profileEntry(
    onOpenCatalog: () -> Unit,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenWhatsNew: () -> Unit,
    onOpenLinkedInLogin: (() -> Unit)? = null,
    debugSections: (@Composable () -> Unit)? = null,
    visibility: VisibilitySlot? = null,
) {
    entry<ProfileKey> {
        ProfileRoute(onBack = onBack, onOpenSettings = onOpenSettings, onConnectLinkedIn = onOpenLinkedInLogin)
    }
    entry<SettingsKey> {
        SettingsRoute(
            onBack = onBack,
            onOpenCatalog = onOpenCatalog,
            onOpenWhatsNew = onOpenWhatsNew,
            debugSections = debugSections,
            visibility = visibility,
        )
    }
    entry<WhatsNewKey> {
        WhatsNewRoute(onBack = onBack)
    }
    entry<LinkedInLoginKey> {
        LinkedInLoginRoute(onDone = onBack)
    }
}
