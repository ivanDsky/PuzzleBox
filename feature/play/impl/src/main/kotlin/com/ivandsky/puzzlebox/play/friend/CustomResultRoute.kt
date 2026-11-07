package com.ivandsky.puzzlebox.play.friend

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.data.model.PostState
import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.CompactTopBar
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PillShape
import com.ivandsky.puzzlebox.designsystem.PrimaryButton
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.SecondaryButton
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.colors
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.designsystem.Stamp
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.GameGlyph
import com.ivandsky.puzzlebox.gameui.GameGlyphSize
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.api.CustomResultKey
import java.time.Instant
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * A solved friend's puzzle on the Play stack: the result card over the key's solve. Done
 * and Up are [onDone], back to the puzzle's card; Play again resets the finished play
 * before [onPlayAgain] reopens its board, so the board never re-hands off the old solve.
 */
@Composable
internal fun CustomResultRoute(
    key: CustomResultKey,
    onPlayAgain: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: CustomResultViewModel = koinViewModel(key = key.toString(), parameters = { parametersOf(key) })
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(modifier.fillMaxSize().testTag("route.custom_result")) {
        CompactTopBar(
            title = "",
            navigationIcon = {
                IconButton(onClick = onDone, modifier = Modifier.testTag("custom_result.up")) {
                    Icon(PuzzleIcons.Back, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
        )
        CustomResultScreen(
            state = state,
            onDone = onDone,
            onPlayAgain = {
                viewModel.playAgain()
                onPlayAgain()
            },
        )
    }
}

/**
 * The result card: the puzzle (glyph stamped on entry, "<game> · <title>", the author), the
 * time as the hero number, the standing — a Record pill stamped once the solve is recorded,
 * or the record a replay leaves standing — the Author and Hints tiles, Done and Play again.
 * A friend's puzzle never counts toward a streak, so there is none, nor a daily's share card.
 */
@Composable
internal fun CustomResultScreen(
    state: CustomResultState,
    onDone: () -> Unit,
    onPlayAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        OutlinedCard(modifier = Modifier.widthIn(max = CardMaxWidth).fillMaxWidth(), shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                Header(state)
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(
                        text = formatDuration(state.timeMs),
                        style = MaterialTheme.typography.displayLarge.tabular(),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("custom_result.time"),
                    )
                    Standing(state.standing, state.brand)
                }
                Tiles(state)
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    PrimaryButton("Done", onClick = onDone, modifier = Modifier.fillMaxWidth().testTag("custom_result.done"))
                    SecondaryButton("Play again", onClick = onPlayAgain, modifier = Modifier.fillMaxWidth().testTag("custom_result.playAgain"))
                }
            }
        }
        Text(
            text = "Friends' puzzles never touch your dailies' streak, history or bests.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = CardMaxWidth).padding(top = Spacing.md),
        )
    }
}

@Composable
private fun Header(state: CustomResultState) {
    val inspection = LocalInspectionMode.current
    var entered by rememberSaveable { mutableStateOf(inspection) }
    LaunchedEffect(Unit) { entered = true }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(GameGlyphSize.Tile.box).testTag("custom_result.stamp")) {
            Stamp(visible = entered) { GameGlyph(state.brand, GameGlyphSize.Tile) }
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = listOfNotNull(state.gameName, state.puzzle?.title).joinToString(" · "),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("custom_result.title"),
            )
            state.puzzle?.let { puzzle ->
                Text(
                    text = "by ${puzzle.authorName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** The solve's standing in a slot reserved at the pill's height, so nothing shifts when it lands. */
@Composable
private fun Standing(standing: CustomStanding?, brand: GameBrand) {
    val reduced = rememberReducedMotion()
    val accent = brand.accent.colors()
    Box(Modifier.heightIn(min = StandingHeight), contentAlignment = Alignment.CenterStart) {
        Stamp(visible = standing == CustomStanding.Record) {
            Surface(
                shape = PillShape,
                color = accent.container,
                contentColor = accent.onContainer,
                modifier = Modifier.heightIn(min = StandingHeight).testTag("custom_result.record"),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(PuzzleIcons.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text("Record", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        val fade = tween<Float>(if (reduced) MotionTokens.ReducedFadeDuration else MotionTokens.EnterDuration)
        AnimatedVisibility(visible = standing is CustomStanding.Replay, enter = fadeIn(fade), exit = fadeOut(fade)) {
            val record = (standing as? CustomStanding.Replay)?.recordTimeMs ?: 0L
            Text(
                text = "Replay — your record stays ${formatDuration(record)}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("custom_result.replay"),
            )
        }
    }
}

@Composable
private fun Tiles(state: CustomResultState) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Tile("Author", state.puzzle?.authorTimeMs?.let(::formatDuration) ?: "—", "custom_result.author", Modifier.weight(1f))
        Tile("Hints", if (state.hintsUsed == 0) "None" else state.hintsUsed.toString(), "custom_result.hints", Modifier.weight(1f))
    }
}

@Composable
private fun Tile(label: String, value: String, tag: String, modifier: Modifier) {
    Column(
        modifier.fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.medium)
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.tabular().copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag(tag),
        )
    }
}

private val CardMaxWidth = 420.dp
private val StandingHeight = 28.dp

private fun previewState(standing: CustomStanding?, timeMs: Long = 118_000L, hints: Int = 0) = CustomResultState(
    timeMs = timeMs,
    hintsUsed = hints,
    gameName = "Queens",
    brand = GameBrand.Neutral.copy(accent = AccentSlot.Pink),
    puzzle = ReceivedPuzzle(
        code = requireNotNull(ShareCode.parse("K7Q2MX")),
        gameId = GameId("queens"),
        variant = VariantId("queens/n8"),
        sharedFormatVersion = 1,
        puzzleBytes = ByteArray(0),
        title = "Crown the castle",
        authorName = "Jonas",
        note = "",
        authorTimeMs = 102_000L,
        authorHints = 0,
        authorUid = "uid-jonas",
        openedAt = Instant.EPOCH,
        unpublished = false,
        postState = PostState.Pending,
        postAttempts = 0,
        difficulty = null,
    ),
    standing = standing,
)

@Composable
private fun PreviewScreen(state: CustomResultState) {
    PuzzleBoxTheme {
        Surface(color = MaterialTheme.colorScheme.surface) { CustomResultScreen(state, onDone = {}, onPlayAgain = {}) }
    }
}

// Preview: the first solve here, stamped Record.
@PuzzleBoxPreviews
@Composable
internal fun CustomResultRecordPreview() {
    PreviewScreen(previewState(CustomStanding.Record))
}

// Preview: a slower replay with two hints; the record stays.
@PuzzleBoxPreviews
@Composable
internal fun CustomResultReplayPreview() {
    PreviewScreen(previewState(CustomStanding.Replay(recordTimeMs = 118_000L), timeMs = 131_000L, hints = 2))
}

// Preview: the solve's row not yet read; the standing slot holds its place.
@PuzzleBoxPreviews
@Composable
internal fun CustomResultPendingPreview() {
    PreviewScreen(previewState(standing = null))
}
