package com.ivandsky.puzzlebox.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.airbnb.android.showkase.annotation.ShowkaseComposable

/**
 * The app's one icon set (identity §2.5, spec A3): every icon call site reads a member
 * here, never `Icons.*` directly. Members the frozen material-icons-core set carries are
 * its Rounded glyphs (Outlined for the unselected nav states); the rest are Material
 * Symbols Rounded 24dp drawables in res/drawable (`ic_<symbol name>.xml`, FILL 0), read
 * through a `@Composable` getter. [Box]/[BoxFilled] are the custom four-tile mark
 * (BoxMark.kt). Game glyphs are not here: each game owns its glyph on its brand.
 */
object PuzzleIcons {
    val Home: ImageVector = Icons.Outlined.Home
    val HomeFilled: ImageVector = Icons.Rounded.Home
    val Box: ImageVector = BoxMarkOutlined
    val BoxFilled: ImageVector = BoxMarkFilled
    val Create: ImageVector = Icons.Outlined.Edit
    val CreateFilled: ImageVector = Icons.Rounded.Edit
    val Profile: ImageVector = Icons.Outlined.Person
    val ProfileFilled: ImageVector = Icons.Rounded.Person
    /** The "+" of the New puzzle button. */
    val Add: ImageVector = Icons.Rounded.Add
    val Chevron: ImageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight
    val Back: ImageVector = Icons.AutoMirrored.Rounded.ArrowBack
    val Check: ImageVector = Icons.Rounded.Check
    val Close: ImageVector = Icons.Rounded.Close
    val Search: ImageVector = Icons.Rounded.Search
    val ExpandMore: ImageVector = Icons.Rounded.KeyboardArrowDown

    /** Overflow menu: three dots stacked. */
    val More: ImageVector = Icons.Rounded.MoreVert

    /** Appearance setting: the half-filled `contrast` circle. */
    val Appearance: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_contrast)

    /** "How to play": `help`, a ? in a circle. */
    val HowTo: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_help)

    val Hint: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_lightbulb)

    val Undo: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_undo)

    val Redo: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_redo)

    /** Clear the board: `ink_eraser`. */
    val Clear: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_ink_eraser)

    /** Erase one cell: `backspace`. */
    val Erase: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_backspace)

    /** Notes mode, off: the pencil outline. */
    val Notes: ImageVector = Icons.Outlined.Edit

    /** Notes mode, on: the filled pencil. */
    val NotesOn: ImageVector = Icons.Rounded.Edit

    /** Streak: `local_fire_department`. */
    val Flame: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_local_fire_department)

    /** Hub status: not started, a ring (StatusMarkIcons.kt). */
    val StatusRing: ImageVector = StatusRingMark

    /** Hub status: in progress, the ring with a filled half disc. */
    val StatusHalf: ImageVector = StatusHalfMark

    /** Hub status: offline, tap to retry. */
    val Retry: ImageVector = RetryMark

    /** Past day status: the puzzle is saved on this device, a phone outline. */
    val SavedOnDevice: ImageVector = SavedOnDeviceMark

    /** Past day status, and the offline pill: the puzzle needs a connection, a cloud. */
    val NeedsConnection: ImageVector = NeedsConnectionMark

    /** Copy to the clipboard: two outlined sheets, the back one tucked under the front. */
    val Copy: ImageVector = CopyMark
}

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
            "Close" to PuzzleIcons.Close,
            "Search" to PuzzleIcons.Search,
            "ExpandMore" to PuzzleIcons.ExpandMore,
            "StatusRing" to PuzzleIcons.StatusRing,
            "StatusHalf" to PuzzleIcons.StatusHalf,
            "Retry" to PuzzleIcons.Retry,
            "SavedOnDevice" to PuzzleIcons.SavedOnDevice,
            "NeedsConnection" to PuzzleIcons.NeedsConnection,
            "Add" to PuzzleIcons.Add,
            "Copy" to PuzzleIcons.Copy,
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
