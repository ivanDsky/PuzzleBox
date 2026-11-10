package com.ivandsky.puzzlebox.app.nav

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
import androidx.compose.foundation.layout.RowScope
import com.ivandsky.puzzlebox.designsystem.LocalShellActions
import com.ivandsky.puzzlebox.home.api.HomeKey
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.gameui.PlayerAvatar
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.profile.api.ProfileKey
import kotlinx.coroutines.flow.StateFlow

/**
 * The shell's chrome for Browse screens (ADR-0027): the avatar (→ Profile), pushed on the
 * current tab, in one fixed place. With [railActions] the rail carries it at its bottom
 * ([RailChromeActions]) and no header does; otherwise only Today's header draws it.
 * Settings is reached from Profile's own gear. Everything else passes through unchanged.
 *
 * [LocalShellActions] is static: a new value would recompose every Browse subtree. The one
 * actions lambda is built here, once per decorator, and collects [identity] inside its own
 * body, so a sign-in or a photo load recomposes only the slot.
 */
internal fun shellChromeDecorator(
    navigator: Navigator,
    identity: StateFlow<Identity?>,
    railActions: Boolean,
): NavEntryDecorator<NavKey> {
    val onProfile = { navigator.pushOnce(ProfileKey) }
    val actions: @Composable RowScope.() -> Unit = {
        val current by identity.collectAsStateWithLifecycle()
        ShellChromeActions(current, onProfile)
    }
    return NavEntryDecorator { entry ->
        val key = entry.metadata[ENTRY_KEY]
        if (!railActions && key is HomeKey) {
            CompositionLocalProvider(LocalShellActions provides actions) { entry.Content() }
        } else {
            entry.Content()
        }
    }
}

/** The rail's bottom slot: the avatar, pushing Profile on the current tab. */
@Composable
internal fun RailChromeActions(navigator: Navigator, identity: StateFlow<Identity?>) {
    val current by identity.collectAsStateWithLifecycle()
    ShellChromeActions(current, { navigator.pushOnce(ProfileKey) })
}

/**
 * The avatar: a 48dp target like the bars' own actions. Signed in, it
 * is the player's photo over their initial; signed out, the Profile icon.
 */
@Composable
internal fun ShellChromeActions(identity: Identity?, onProfile: () -> Unit) {
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
}

/** A touch larger than the 24dp icons beside it, so a face reads at a glance. */
private val AvatarSize = 28.dp
