package com.ivandsky.puzzlebox.gameui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivandsky.puzzlebox.designsystem.MedalBronze
import com.ivandsky.puzzlebox.designsystem.MedalBronzeInk
import com.ivandsky.puzzlebox.designsystem.MedalGold
import com.ivandsky.puzzlebox.designsystem.MedalGoldInk
import com.ivandsky.puzzlebox.designsystem.MedalSilver
import com.ivandsky.puzzlebox.designsystem.MedalSilverInk
import com.ivandsky.puzzlebox.designsystem.tabular

internal val RankCellSize = 28.dp
private val DiscSize = 26.dp
private val MedalDigitSize = 14.sp

// The "100" is the backend board cap, whose constant lives outside game-ui's reach (see DailyBoardNote.PostedOutsideTop).
internal const val BeyondPageLabel = "100+"

private fun medalFill(place: Int): Color? = when (place) {
    1 -> MedalGold
    2 -> MedalSilver
    3 -> MedalBronze
    else -> null
}

private fun medalInk(place: Int): Color = when (place) {
    1 -> MedalGoldInk
    2 -> MedalSilverInk
    else -> MedalBronzeInk
}

/**
 * A row's place cell. Where the board has the LinkedIn account's motion the podium places are medals (see
 * [LocalPlaceMotion]) and a change of place rolls like an odometer; a board without it draws the plain number, as it
 * always did. [tag] marks the cell, and its text is the place the row is at, whatever the roll is showing.
 * A row [beyondPage] is at no place the board knows, so the cell says "100+" and never rolls or wears a medal.
 */
@Composable
internal fun RankPlace(place: Int?, quiet: Color, tag: String, beyondPage: Boolean = false) {
    val motion = LocalPlaceMotion.current
    when {
        beyondPage -> Text(
            BeyondPageLabel,
            style = MaterialTheme.typography.labelLarge.tabular(),
            color = quiet,
            softWrap = false,
            modifier = Modifier.testTag(tag),
        )
        motion == null || place == null -> Text(
            place?.toString() ?: "—",
            style = MaterialTheme.typography.labelLarge.tabular(),
            color = quiet,
            modifier = Modifier.testTag(tag),
        )
        motion.animated -> RankOdometer(place, quiet, motion, tag)
        else -> RankCell(place, quiet, Modifier.placeSemantics(tag, place))
    }
}

/** One number in the place cell: a disc in the medal's colour behind it for places 1 to 3. */
@Composable
internal fun RankCell(place: Int, quiet: Color, modifier: Modifier = Modifier) {
    val fill = medalFill(place)
    Box(modifier.size(RankCellSize), contentAlignment = Alignment.Center) {
        if (fill != null) Box(Modifier.size(DiscSize).background(fill, CircleShape))
        Text(
            "$place",
            style = MaterialTheme.typography.labelLarge.tabular().let {
                if (fill != null) it.copy(fontSize = MedalDigitSize, fontWeight = FontWeight.ExtraBold) else it
            },
            color = if (fill != null) medalInk(place) else quiet,
        )
    }
}

// The cell says the place it is at as one piece of text, not the numbers a roll passes on the way, and not the disc.
internal fun Modifier.placeSemantics(tag: String, place: Int): Modifier =
    testTag(tag).clearAndSetSemantics { text = AnnotatedString("$place") }
