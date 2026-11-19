package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.data.visibility.OwnTier
import com.ivandsky.puzzlebox.data.visibility.VisibilityTier
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.history.RanksTuneDefaults
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The hub's previews' one host: the theme on a surface of the screen's background colour, so a dark preview draws on a dark
 * page rather than on the preview tool's light one. [modifier] sizes the surface, for a preview of a whole screen.
 */
@Composable
internal fun RanksPreviewHost(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    PuzzleBoxTheme {
        Surface(modifier, color = MaterialTheme.colorScheme.background, content = content)
    }
}

/** The Tune sheet's defaults for a preview or a test: a chosen Duel tier, the hub landing on Friends and All games, writes ignored. */
internal fun previewTuneDefaults() = RanksTuneDefaults(
    ownTier = MutableStateFlow(OwnTier.Chosen(VisibilityTier.Duel)),
    onSelectTier = {},
    friendsDefault = MutableStateFlow(true),
    onSelectFriendsDefault = {},
    linkedInDefault = MutableStateFlow(false),
    onSelectLinkedInDefault = {},
)
