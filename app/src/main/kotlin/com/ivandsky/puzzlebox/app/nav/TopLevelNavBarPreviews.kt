package com.ivandsky.puzzlebox.app.nav

import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.gameui.PlayerAvatar
import com.ivandsky.puzzlebox.history.api.HistoryRootKey
import com.ivandsky.puzzlebox.home.api.HomeKey
import com.ivandsky.puzzlebox.play.api.PlayKey

@PuzzleBoxPreviews
@Composable
internal fun TopLevelNavBarNavV2Preview() {
    PuzzleBoxTheme {
        TopLevelNavBar(
            selected = HistoryRootKey,
            onSelect = {},
            badged = setOf(PlayKey),
            destinations = topLevelDestinations(navV2 = true),
        )
    }
}

/** The NavV2 rail: the avatar slot mirrors [ShellAvatar]'s Rail size (a 36dp avatar in a 48dp button). */
@PuzzleBoxPreviews
@Composable
internal fun TopLevelNavRailNavV2Preview() {
    PuzzleBoxTheme {
        TopLevelNavRail(
            selected = HomeKey,
            onSelect = {},
            badged = setOf(PlayKey),
            destinations = topLevelDestinations(navV2 = true),
            bottomActions = {
                IconButton(onClick = {}) {
                    PlayerAvatar(name = "Ann", photoUrl = null, size = ShellAvatarSize.Rail.photo)
                }
            },
        )
    }
}
