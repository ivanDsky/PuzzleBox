package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.ivandsky.puzzlebox.designsystem.AppDropdownMenu
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PillShape
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.pressScale
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.model.GameId

/**
 * The hub's title: the current board's [title] and a chevron; another board's name frosts over the old one, as far as
 * the [scrub] is between the two when there is one ([BoardScrubCell]). A tap drops a menu of every board in [places]
 * with the viewer's place on each (a pick reports [onPick]; the board re-ranks in place) and a "How ranks work" row
 * ([onHowRanksWork]). The menu grows out of the title and its rows rise in one after another; reduced motion drops
 * the travel and keeps the fades.
 */
@Composable
internal fun RanksTitleMenu(
    title: String,
    places: List<BoardPlace>,
    selected: RanksBoard?,
    onPick: (RanksBoard) -> Unit,
    onHowRanksWork: () -> Unit,
    modifier: Modifier = Modifier,
    scrub: BoardScrub? = null,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val reduced = rememberReducedMotion()
    val interaction = remember { MutableInteractionSource() }
    val turn by animateFloatAsState(
        targetValue = if (open) CHEVRON_OPEN else 0f,
        animationSpec = if (reduced) tween(0) else spring(MotionTokens.SpringDampingDefault, MotionTokens.SpringStiffnessDefault),
        label = "titleChevron",
    )
    Box(modifier) {
        Row(
            Modifier
                .testTag("ranks.title")
                .pressScale(interaction, reduced)
                .clip(PillShape)
                .clickable(interaction, LocalIndication.current, role = Role.Button, onClickLabel = "Choose board") { open = true }
                .defaultMinSize(minHeight = RanksMetrics.TitleMinHeight)
                .padding(start = Spacing.sm)
                .semantics { stateDescription = if (open) "Expanded" else "Collapsed" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BoardScrubCell(scrub, places.indexOfFirst { it.board == selected }, title, at = { places.getOrNull(it)?.name }) {
                Text(it, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            }
            Icon(
                PuzzleIcons.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.graphicsLayer { rotationZ = turn },
            )
        }
        AppDropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.testTag("ranks.menu")) {
            places.forEachIndexed { index, place ->
                BoardItem(place, selected = place.board == selected, index, reduced) {
                    open = false
                    onPick(place.board)
                }
            }
            HorizontalDivider(Modifier.padding(vertical = Spacing.xs), color = MaterialTheme.colorScheme.outlineVariant)
            MenuRow(places.size, reduced, Modifier.testTag("ranks.menu.how")) {
                DropdownMenuItem(
                    text = { Text("How ranks work", style = MaterialTheme.typography.bodyLarge) },
                    leadingIcon = { Icon(PuzzleIcons.HowTo, contentDescription = null, modifier = Modifier.size(RanksMetrics.IconSize)) },
                    onClick = {
                        open = false
                        onHowRanksWork()
                    },
                )
            }
        }
    }
}

private const val CHEVRON_OPEN = 180f

private val PreviewPlaces = listOf(
    BoardPlace(RanksBoard.Overall, "Overall", Standing.Place(12)),
    BoardPlace(RanksBoard.Game(GameId("zip")), "Zip", Standing.Place(3)),
    BoardPlace(RanksBoard.Game(GameId("tango")), "Tango", Standing.Beyond),
    BoardPlace(RanksBoard.Game(GameId("queens")), "Queens", Standing.None),
    BoardPlace(RanksBoard.Game(GameId("strands")), "Strands", Standing.Pending),
)

@PuzzleBoxPreviews
@Composable
private fun RanksTitleMenuPreview() {
    PuzzleBoxTheme {
        Box(Modifier.padding(Spacing.lg)) {
            RanksTitleMenu("Overall", PreviewPlaces, selected = RanksBoard.Overall, onPick = {}, onHowRanksWork = {})
        }
    }
}
