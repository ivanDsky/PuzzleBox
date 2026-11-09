package com.ivandsky.puzzlebox.app.nav

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.designsystem.LocalShellActions
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.gameui.PlayerAvatar
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.profile.api.ProfileKey
import com.ivandsky.puzzlebox.profile.api.SettingsKey
import kotlinx.coroutines.flow.StateFlow

/**
 * The shell's chrome for Browse screens (ADR-0027): wraps every entry whose placement
 * chrome is [Chrome.Browse] so its top bar draws the avatar (→ Profile) and the gear
 * (→ Settings), each pushed on the current tab. Immersive and Tool entries pass through
 * unchanged, so Profile and Settings never link to themselves.
 *
 * [LocalShellActions] is static: a new value would recompose every Browse subtree. The one
 * actions lambda is built here, once per decorator, and collects [identity] inside its own
 * body, so a sign-in or a photo load recomposes only the slot.
 */
internal fun shellChromeDecorator(navigator: Navigator, identity: StateFlow<Identity?>): NavEntryDecorator<NavKey> {
    val onProfile = { navigator.pushOnce(ProfileKey) }
    val onSettings = { navigator.pushOnce(SettingsKey) }
    val actions: @Composable RowScope.() -> Unit = {
        val current by identity.collectAsStateWithLifecycle()
        ShellChromeActions(current, onProfile, onSettings)
    }
    return NavEntryDecorator { entry ->
        val key = entry.metadata[ENTRY_KEY]
        if (key != null && placements[key::class]?.chrome == Chrome.Browse) {
            CompositionLocalProvider(LocalShellActions provides actions) { entry.Content() }
        } else {
            entry.Content()
        }
    }
}

/**
 * The avatar, then the gear: 48dp targets like the bars' own actions. Signed in, the avatar
 * is the player's photo over their initial; signed out, the Profile icon.
 */
@Composable
internal fun RowScope.ShellChromeActions(identity: Identity?, onProfile: () -> Unit, onSettings: () -> Unit) {
    IconButton(
        onClick = onProfile,
        modifier = Modifier.testTag("shell.avatar").semantics { contentDescription = "Profile" },
    ) {
        if (identity == null) {
            Icon(imageVector = PuzzleIcons.Profile, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            PlayerAvatar(name = identity.displayName, photoUrl = identity.photoUrl, size = AvatarSize)
        }
    }
    IconButton(onClick = onSettings, modifier = Modifier.testTag("shell.settings")) {
        Icon(imageVector = PuzzleIcons.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A touch larger than the 24dp icons beside it, so a face reads at a glance. */
private val AvatarSize = 28.dp
