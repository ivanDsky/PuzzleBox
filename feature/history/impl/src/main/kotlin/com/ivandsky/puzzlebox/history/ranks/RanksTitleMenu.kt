package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import kotlin.math.min
import kotlinx.coroutines.delay

private val ICON = 20.dp
private val PLACE_MIN_WIDTH = 48.dp

/**
 * The hub's title: the current board's [title] and a chevron. A tap drops a menu of every board in [places] with the
 * viewer's place on each (a pick reports [onPick]; the board re-ranks in place) and a "How ranks work" row
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
                .defaultMinSize(minHeight = 48.dp)
                .padding(start = Spacing.sm)
                .semantics { stateDescription = if (open) "Expanded" else "Collapsed" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
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
                    leadingIcon = { Icon(PuzzleIcons.HowTo, contentDescription = null, modifier = Modifier.size(ICON)) },
                    onClick = {
                        open = false
                        onHowRanksWork()
                    },
                )
            }
        }
    }
}

@Composable
private fun BoardItem(place: BoardPlace, selected: Boolean, index: Int, reduced: Boolean, onClick: () -> Unit) {
    MenuRow(index, reduced, Modifier.testTag("ranks.menu.item.$index")) {
        DropdownMenuItem(
            text = {
                Text(
                    place.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (selected) FontWeight.Bold else null,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            },
            leadingIcon = {
                if (selected) Icon(PuzzleIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(ICON))
                else Spacer(Modifier.size(ICON))
            },
            trailingIcon = { StandingText(place.standing, reduced) },
            onClick = onClick,
            modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "${place.name}, ${place.standing.describe()}" },
        )
    }
}

/** A place that changes (the boards answering, a window switch) crossfades in place rather than snapping. */
@Composable
private fun StandingText(standing: Standing, reduced: Boolean) {
    Crossfade(standing.label(), animationSpec = tween(if (reduced) MotionTokens.ReducedFadeDuration else MotionTokens.DurationMedium), label = "place") { label ->
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(min = PLACE_MIN_WIDTH).padding(start = Spacing.sm),
        )
    }
}

/** A menu row that rises and fades in [index]th in line once the menu opens (the stagger is capped); reduced motion: a plain fade. */
@Composable
private fun MenuRow(index: Int, reduced: Boolean, modifier: Modifier, content: @Composable () -> Unit) {
    val still = LocalInspectionMode.current
    val enter = remember { Animatable(if (still) 1f else 0f) }
    val rise = with(LocalDensity.current) { MotionTokens.EnterRise.toPx() }
    LaunchedEffect(Unit) {
        if (still) return@LaunchedEffect
        delay(min(index, MotionTokens.EnterStaggerCap).toLong() * MotionTokens.EnterStagger)
        val duration = if (reduced) MotionTokens.ReducedFadeDuration else MotionTokens.EnterDuration
        enter.animateTo(1f, tween(duration, easing = MotionTokens.EasingEmphasizedDecelerate))
    }
    Box(
        modifier.graphicsLayer {
            alpha = enter.value
            translationY = if (reduced) 0f else (1f - enter.value) * rise
        },
    ) { content() }
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
