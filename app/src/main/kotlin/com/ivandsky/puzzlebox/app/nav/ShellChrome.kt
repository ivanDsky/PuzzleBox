package com.ivandsky.puzzlebox.app.nav

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.designsystem.LocalShellActions
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.gameui.PlayerAvatar
import com.ivandsky.puzzlebox.home.api.HomeKey
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.profile.api.ProfileKey
import kotlinx.coroutines.flow.StateFlow

/**
 * The shell's chrome (ADR-0027): the avatar ([ShellAvatar], → Profile), pushed on the
 * current tab, in one fixed place. With [avatarInTodayHeader] Today's header draws it;
 * otherwise the rail carries it at its bottom and no header does.
 * Settings is reached from Profile's own gear. Everything else passes through unchanged.
 *
 * [LocalShellActions] is static: a new value would recompose every Browse subtree. The one
 * actions lambda is built here, once per decorator, and [ShellAvatar] collects the identity
 * inside its own body, so a sign-in or a photo load recomposes only the slot.
 */
internal fun shellChromeDecorator(
    navigator: Navigator,
    identity: StateFlow<Identity?>,
    avatarInTodayHeader: Boolean,
): NavEntryDecorator<NavKey> {
    val actions: @Composable RowScope.() -> Unit = {
        ShellAvatar(navigator, identity, ShellAvatarSize.Header)
    }
    return NavEntryDecorator { entry ->
        val key = entry.metadata[ENTRY_KEY]
        if (avatarInTodayHeader && key is HomeKey) {
            CompositionLocalProvider(LocalShellActions provides actions) { entry.Content() }
        } else {
            entry.Content()
        }
    }
}

/** The shell's one avatar: collects [identity] and pushes Profile on the current tab. */
@Composable
internal fun ShellAvatar(
    navigator: Navigator,
    identity: StateFlow<Identity?>,
    size: ShellAvatarSize,
    modifier: Modifier = Modifier,
) {
    val current by identity.collectAsStateWithLifecycle()
    AvatarButton(current, { navigator.pushOnce(ProfileKey) }, size, modifier)
}

/** Where the avatar sits: the header's is a touch larger than the 24dp icons beside it; the rail's matches its larger tabs. */
internal enum class ShellAvatarSize(val photo: Dp, val icon: Dp) {
    Header(28.dp, 24.dp),
    Rail(36.dp, 36.dp),
}

/**
 * The avatar: a 48dp target like the bars' own actions. Signed in, it
 * is the player's photo over their initial; signed out, the Profile icon.
 */
@Composable
private fun AvatarButton(identity: Identity?, onClick: () -> Unit, size: ShellAvatarSize, modifier: Modifier) {
    IconButton(
        onClick = onClick,
        modifier = modifier.testTag("shell.avatar").semantics { contentDescription = "Profile" },
    ) {
        if (identity == null) {
            Icon(
                imageVector = PuzzleIcons.Profile,
                contentDescription = null,
                modifier = Modifier.size(size.icon),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            PlayerAvatar(name = identity.displayName, photoUrl = identity.photoUrl, size = size.photo)
        }
    }
}
