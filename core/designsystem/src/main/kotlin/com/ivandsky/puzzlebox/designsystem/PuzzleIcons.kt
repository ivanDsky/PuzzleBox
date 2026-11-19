package com.ivandsky.puzzlebox.designsystem

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource

/**
 * The app's one icon set: every icon call site reads a member
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

    /** A neutral notice: the circled "i" from the core set. */
    val Info: ImageVector = Icons.Rounded.Info

    /** Overflow menu: three dots stacked. */
    val More: ImageVector = Icons.Rounded.MoreVert

    /** Community tab, unselected: Material `group`, two people, outlined. */
    val Community: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_group_outlined)

    /** Community tab, selected: Material `group`, two solid people. */
    val CommunityFilled: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_group_filled)

    /**
     * History tab, unselected: Material Symbols `schedule` (FILL 0), a clock face.
     */
    val History: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_schedule_outlined)

    /**
     * History tab, selected: Material Symbols `schedule` (FILL 1), a solid clock disc with
     * the hands cut out.
     */
    val HistoryFilled: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_schedule_filled)

    /** The shell's gear: the Outlined settings glyph from the core set. */
    val Settings: ImageVector = Icons.Outlined.Settings

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

    /** Roll a new random value, e.g. a draft codename: a die showing five. */
    val Dice: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_casino)

    /** Notes mode, off: the pencil outline. */
    val Notes: ImageVector = Icons.Outlined.Edit

    /** Notes mode, on: the filled pencil. */
    val NotesOn: ImageVector = Icons.Rounded.Edit

    /** Streak: `local_fire_department`. */
    val Flame: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_local_fire_department)

    /** A streak freeze: `ac_unit`, a snowflake. */
    val Snowflake: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_ac_unit)

    /** Closed to new entries: `lock`. */
    val Lock: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_lock)

    /** A shared link that no longer leads anywhere: `link_off`. */
    val LinkOff: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_link_off)

    /** Content that failed its integrity check: `gpp_bad`, a shield with a cross. */
    val FailedCheck: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_gpp_bad)

    /** Needs a newer PuzzleBox: `system_update`. */
    val Update: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_system_update)

    /** Hub status: not started, a ring (StatusMarkIcons.kt). */
    val StatusRing: ImageVector = StatusRingMark

    /** Hub status: in progress, the ring with a filled half disc. */
    val StatusHalf: ImageVector = StatusHalfMark

    /** Hub status: offline, tap to retry. */
    val Retry: ImageVector = RetryMark

    /** Duel summary: watch the replay again, a ring with a play triangle. Play again keeps the retry arrow. */
    val WatchAgain: ImageVector = WatchAgainMark

    /** Past day status: the puzzle is saved on this device, a phone outline. */
    val SavedOnDevice: ImageVector = SavedOnDeviceMark

    /** Past day status, and the offline pill: the puzzle needs a connection, a cloud. */
    val NeedsConnection: ImageVector = NeedsConnectionMark

    /** Copy to the clipboard: two outlined sheets, the back one tucked under the front. */
    val Copy: ImageVector = CopyMark

    /** Duel result and record: `emoji_events`, the trophy. */
    val Trophy: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_emoji_events)

    /** Share a result or invite: the share glyph from the core set. */
    val Share: ImageVector = Icons.Rounded.Share

    /** A duel invite: the envelope from the core set. */
    val Mail: ImageVector = Icons.Rounded.Email

    /** A duel waiting on my move: the play triangle from the core set. */
    val Play: ImageVector = Icons.Rounded.PlayArrow

    /** A duel waiting on the other player: `hourglass_empty`. */
    val Hourglass: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_hourglass_empty)

    /** Time left: `schedule`, a clock face. */
    val Clock: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_schedule)

    /** A drawn duel: `equal`. */
    val Equals: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_equal)

    /** Post a result out: `upload`, an arrow up out of a tray. */
    val Upload: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_upload)

    /** Difficulty, Easy: four ascending bars, the first filled (LevelBarsMark.kt). */
    val LevelEasy: ImageVector = LevelEasyMark

    /** Difficulty, Medium: two bars filled. */
    val LevelMedium: ImageVector = LevelMediumMark

    /** Difficulty, Hard: three bars filled. */
    val LevelHard: ImageVector = LevelHardMark

    /** Difficulty, Expert: all four bars filled. */
    val LevelExpert: ImageVector = LevelExpertMark
}
