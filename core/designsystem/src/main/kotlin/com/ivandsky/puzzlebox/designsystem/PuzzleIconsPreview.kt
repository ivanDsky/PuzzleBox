package com.ivandsky.puzzlebox.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.airbnb.android.showkase.annotation.ShowkaseComposable

private const val PREVIEW_COLUMNS = 4
private val PreviewCellWidth = 76.dp
private val PreviewIconSize = 24.dp

// Icon sheet for the catalog: every PuzzleIcons member at 24dp in inkMuted with its
// name, in light and dark.
@ShowkaseComposable(name = "PuzzleIcons", group = "Icons")
@PuzzleBoxPreviews
@Composable
internal fun PuzzleIconsPreview() {
    PuzzleBoxTheme {
        val icons = listOf(
            "Home" to PuzzleIcons.Home,
            "HomeFilled" to PuzzleIcons.HomeFilled,
            "Box" to PuzzleIcons.Box,
            "BoxFilled" to PuzzleIcons.BoxFilled,
            "Create" to PuzzleIcons.Create,
            "CreateFilled" to PuzzleIcons.CreateFilled,
            "Profile" to PuzzleIcons.Profile,
            "ProfileFilled" to PuzzleIcons.ProfileFilled,
            "History" to PuzzleIcons.History,
            "HistoryFilled" to PuzzleIcons.HistoryFilled,
            "Community" to PuzzleIcons.Community,
            "CommunityFilled" to PuzzleIcons.CommunityFilled,
            "Settings" to PuzzleIcons.Settings,
            "Appearance" to PuzzleIcons.Appearance,
            "HowTo" to PuzzleIcons.HowTo,
            "Hint" to PuzzleIcons.Hint,
            "Undo" to PuzzleIcons.Undo,
            "Redo" to PuzzleIcons.Redo,
            "Clear" to PuzzleIcons.Clear,
            "Erase" to PuzzleIcons.Erase,
            "Notes" to PuzzleIcons.Notes,
            "NotesOn" to PuzzleIcons.NotesOn,
            "Chevron" to PuzzleIcons.Chevron,
            "Back" to PuzzleIcons.Back,
            "Check" to PuzzleIcons.Check,
            "Flame" to PuzzleIcons.Flame,
            "Snowflake" to PuzzleIcons.Snowflake,
            "Lock" to PuzzleIcons.Lock,
            "LinkOff" to PuzzleIcons.LinkOff,
            "FailedCheck" to PuzzleIcons.FailedCheck,
            "Update" to PuzzleIcons.Update,
            "Close" to PuzzleIcons.Close,
            "Filter" to PuzzleIcons.Filter,
            "Search" to PuzzleIcons.Search,
            "ExpandMore" to PuzzleIcons.ExpandMore,
            "StatusRing" to PuzzleIcons.StatusRing,
            "StatusHalf" to PuzzleIcons.StatusHalf,
            "Retry" to PuzzleIcons.Retry,
            "WatchAgain" to PuzzleIcons.WatchAgain,
            "SavedOnDevice" to PuzzleIcons.SavedOnDevice,
            "NeedsConnection" to PuzzleIcons.NeedsConnection,
            "Add" to PuzzleIcons.Add,
            "Copy" to PuzzleIcons.Copy,
            "Trophy" to PuzzleIcons.Trophy,
            "Share" to PuzzleIcons.Share,
            "Mail" to PuzzleIcons.Mail,
            "Play" to PuzzleIcons.Play,
            "Hourglass" to PuzzleIcons.Hourglass,
            "Clock" to PuzzleIcons.Clock,
            "Equals" to PuzzleIcons.Equals,
            "Upload" to PuzzleIcons.Upload,
            "Info" to PuzzleIcons.Info,
        )
        Column(
            modifier = Modifier.background(MaterialTheme.colorScheme.background).padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            icons.chunked(PREVIEW_COLUMNS).forEach { row ->
                Row {
                    row.forEach { (name, icon) ->
                        Column(
                            modifier = Modifier.width(PreviewCellWidth),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(PreviewIconSize),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = name,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
