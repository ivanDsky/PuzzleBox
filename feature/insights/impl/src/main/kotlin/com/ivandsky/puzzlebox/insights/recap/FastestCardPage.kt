package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.RecapCard as PaletteEntry
import com.ivandsky.puzzlebox.designsystem.RecapMotion
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.SwipeMotion
import com.ivandsky.puzzlebox.designsystem.colors
import com.ivandsky.puzzlebox.designsystem.detectSwipe
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.designsystem.ink
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.designsystem.swipeStep
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.launch

/**
 * The Fastest solve page: one mini-card per game played, in game order, starting at [initialGame], each in the accent of its game's brand (Neutral until the route provides them). A swipe steps
 * through the games inside the card, the game tabs say where it is and jump to a game; past the last game a forward swipe (and before the first
 * a back one) is left to the deck. A tap is the deck's, so the replay pill is the only thing here that takes one (A5:
 * an imported time has no pill). Reduced motion crossfades between games.
 */
@Composable
internal fun FastestCardPage(
    card: RecapCard.Fastest,
    periodLabel: String,
    gameName: (GameId) -> String,
    onWatchReplay: (RecapCard.Fastest.Row) -> Unit,
    modifier: Modifier = Modifier,
    initialGame: Int = 0,
) {
    val ink = PaletteEntry.Fastest.ink
    val brands = LocalGameBrands.current
    val reduced = rememberReducedMotion()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val last = card.rows.lastIndex
    var game by remember { mutableIntStateOf(initialGame.coerceIn(0, last)) }
    var stepped by remember { mutableIntStateOf(0) }
    var widthPx by remember { mutableFloatStateOf(1f) }
    val offset = remember { Animatable(0f) }
    val commitPx = with(density) { SwipeMotion.CommitCap.toPx() }
    val flingPx = with(density) { SwipeMotion.FlingVelocity.toPx() }
    // Both a swipe and a tab tap step with the throw: forward throws the card out, back brings it in.
    suspend fun goTo(next: Int) {
        val moved = next != game
        val thrown = RecapMotion.ThrowTravel * widthPx
        when {
            !moved -> offset.animateTo(0f, SwipeMotion.Settle)
            reduced -> game = next
            next > game -> {
                offset.animateTo(thrown, tween(RecapMotion.ThrowDuration, easing = MotionTokens.EasingEmphasizedAccelerate))
                game = next
                offset.snapTo(0f)
            }
            else -> {
                game = next
                offset.snapTo(thrown)
                offset.animateTo(0f, tween(RecapMotion.ThrowBackDuration, easing = MotionTokens.EasingStandard))
            }
        }
        if (moved) stepped++
    }
    val steps = Modifier.pointerInput(last, reduced) {
        detectSwipe(
            canTake = { travel -> if (travel < 0) game < last else game > 0 },
            onDrag = { dx -> if (!reduced) scope.launch { offset.snapTo(offset.value + dx) } },
            onEnd = { drag, velocity ->
                val step = swipeStep(drag, velocity, widthPx, flingPx, minOf(widthPx * SwipeMotion.CommitFraction, commitPx))
                scope.launch { goTo((game + step).coerceIn(0, last)) }
            },
        )
    }
    val names = card.rows.map { gameName(it.game) }
    val tabs: @Composable (Modifier) -> Unit = { m -> FastestGameTabs(names, game, onSelect = { index -> scope.launch { goTo(index) } }, modifier = m) }
    BoxWithConstraints(modifier.fillMaxSize()) {
        // Wide, the tabs sit in the title row where the pips were (tablet); narrow, they take a row under the title.
        val wide = maxWidth >= WideTabsMinWidth
        Column(Modifier.fillMaxSize().padding(horizontal = Spacing.lg).padding(top = Spacing.lg).testTag("recap.fastest")) {
            Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(periodLabel.uppercase(), style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.2.sp), color = ink)
                    Text("Fastest solves", style = MaterialTheme.typography.headlineMedium, color = ink, textAlign = TextAlign.Start)
                }
                if (wide) Box(Modifier.weight(1f).padding(start = Spacing.lg), contentAlignment = Alignment.CenterEnd) { tabs(Modifier) }
            }
            if (!wide) tabs(Modifier.fillMaxWidth().padding(top = Spacing.sm))
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = Spacing.md, bottom = Spacing.md)
                    .onSizeChanged { widthPx = it.width.toFloat() }
                    .then(if (last > 0) steps else Modifier)
                    .testTag("recap.fastest.games"),
            ) {
                Crossfade(game, Modifier.fillMaxSize(), animationSpec = if (reduced) tween(MotionTokens.ReducedFadeDuration) else tween(0), label = "game") { index ->
                    val row = card.rows[index]
                    FastestGameCard(
                        row, gameName(row.game), brands(row.game).accent.colors().accent,
                        startDelayMs = if (stepped > 0) StepDelayMs else 0,
                        onWatchReplay = { onWatchReplay(row) },
                        modifier = Modifier.fillMaxSize().graphicsLayer {
                            translationX = offset.value
                            rotationZ = RecapMotion.DragTilt * offset.value / widthPx
                        },
                    )
                }
            }
        }
    }
}

/** Each game's fastest solve as one flat row of the tablet's selected fact. */
@Composable
internal fun FastestRows(
    rows: List<RecapCard.Fastest.Row>,
    gameName: (GameId) -> String,
    onWatchReplay: (RecapCard.Fastest.Row) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .testTag("recap.fastest.games"),
    ) {
        rows.forEachIndexed { index, row ->
            if (index > 0) HorizontalDivider(Modifier.padding(horizontal = Spacing.lg), color = MaterialTheme.colorScheme.outlineVariant)
            FastestRow(row, gameName(row.game), onWatchReplay)
        }
    }
}

/** One game's row: its name and day, the time, and the play mark where a replay opens. The row reads as one item (a button when it opens a replay). */
@Composable
private fun FastestRow(
    row: RecapCard.Fastest.Row,
    name: String,
    onWatchReplay: (RecapCard.Fastest.Row) -> Unit,
) {
    val time = formatDuration(row.timeMs)
    val day = row.day.shortLabel()
    val open = if (row.hasReplay) {
        Modifier.clickable(onClickLabel = "Watch replay", role = Role.Button) { onWatchReplay(row) }
    } else {
        Modifier
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = RowMinHeight)
            .then(open)
            .padding(start = Spacing.lg)
            .semantics(mergeDescendants = true) {}
            .testTag("recap.fastest.game.${row.game.value}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = Spacing.sm)) {
            Text(text = name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(
                text = if (row.hasReplay) day else "$day · No replay",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(text = time, style = MaterialTheme.typography.titleMedium.tabular(), color = MaterialTheme.colorScheme.onSurface)
        // A fixed slot, so the times of rows with and without a replay line up.
        if (row.hasReplay) {
            Icon(
                PuzzleIcons.Play,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = Spacing.sm).size(PlaySlot),
            )
        } else {
            Spacer(Modifier.size(PlaySlot + Spacing.sm * 2))
        }
    }
}

/** The Fastest solve fact in the tablet list: the title, then each game's time. */
@Composable
internal fun FastestCardFact(
    card: RecapCard.Fastest,
    gameName: (GameId) -> String,
    onWatchReplay: (RecapCard.Fastest.Row) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(Modifier.padding(vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            FactMarker()
            Text(
                text = "Fastest solves",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        FastestRows(card.rows, gameName, onWatchReplay)
    }
}

private val WideTabsMinWidth = 600.dp
private val RowMinHeight = 56.dp
private val PlaySlot = 32.dp
private const val StepDelayMs = 140
