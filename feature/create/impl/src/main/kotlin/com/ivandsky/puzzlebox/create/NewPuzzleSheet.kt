package com.ivandsky.puzzlebox.create

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.launch

private val SizeRowMinHeight = 56.dp

/**
 * The New puzzle sheet (spec A1, A5; 01 handoff "New puzzle sheet"): one [GameStartRow] per
 * game in [games] (glyph tile, one-line rule, default size), then the picked game's sizes,
 * exactly its editor's variants in order. Picking a
 * size slides the sheet away, then reports it through [onPick]; back on the sizes step
 * returns to the games. [initialGame] opens straight on that game's sizes.
 *
 * Motion: the sheet's **Slide in / Slide out** and the scrim's **Fade** are
 * ModalBottomSheet's own; the games ↔ sizes step is a **Crossfade** (DurationMedium in,
 * DurationShort out, EasingStandard), a plain ReducedFadeDuration fade under reduced motion.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewPuzzleSheet(
    games: List<CreateUiState.NewGame>,
    onPick: (gameId: GameId, variant: VariantId) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    initialGame: GameId? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var pickedId by rememberSaveable { mutableStateOf(initialGame?.value) }
    val picked = games.firstOrNull { it.id.value == pickedId }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier.testTag("create.new.sheet"),
        sheetState = sheetState,
    ) {
        NewPuzzleSheetContent(
            games = games,
            picked = picked,
            onGame = { pickedId = it.value },
            onBack = { pickedId = null },
            onSize = { variant ->
                val game = picked ?: return@NewPuzzleSheetContent
                scope.launch { sheetState.hide() }.invokeOnCompletion { onPick(game.id, variant) }
            },
        )
    }
}

@Composable
private fun NewPuzzleSheetContent(
    games: List<CreateUiState.NewGame>,
    picked: CreateUiState.NewGame?,
    onGame: (GameId) -> Unit,
    onBack: () -> Unit,
    onSize: (VariantId) -> Unit,
) {
    val reduced = rememberReducedMotion()
    AnimatedContent(
        targetState = picked,
        transitionSpec = {
            val enter = if (reduced) MotionTokens.ReducedFadeDuration else MotionTokens.DurationMedium
            val exit = if (reduced) MotionTokens.ReducedFadeDuration else MotionTokens.DurationShort
            fadeIn(tween(enter, easing = MotionTokens.EasingStandard)) togetherWith
                fadeOut(tween(exit, easing = MotionTokens.EasingStandard))
        },
        contentKey = { it?.id?.value },
        label = "newPuzzleStep",
    ) { game ->
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = Spacing.lg),
        ) {
            if (game == null) {
                GamesStep(games, onGame)
            } else {
                SizesStep(game, onBack, onSize)
            }
        }
    }
}

@Composable
private fun GamesStep(games: List<CreateUiState.NewGame>, onGame: (GameId) -> Unit) {
    Column(
        Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = "New puzzle",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Pick a game. It saves as a draft while you build — nothing is shared until you publish.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    games.forEach { game ->
        GameStartRow(game = game, tagRoot = "create.new", onClick = { onGame(game.id) }, chevron = true)
    }
}

@Composable
private fun SizesStep(game: CreateUiState.NewGame, onBack: () -> Unit, onSize: (VariantId) -> Unit) {
    Row(
        Modifier.padding(horizontal = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.testTag("create.new.back")) {
            Icon(PuzzleIcons.Back, contentDescription = "Back to games")
        }
        Text(
            text = "${game.name} · pick a size",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
    game.sizes.forEach { size ->
        SizeRow(label = size.label, tag = "create.new.size.${size.variant.value}", onClick = { onSize(size.variant) })
    }
}

@Composable
private fun SizeRow(label: String, tag: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SizeRowMinHeight)
            .clickable(onClick = onClick)
            .testTag(tag)
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@PuzzleBoxPreviews
@Composable
private fun NewPuzzleSheetGamesPreview() {
    PuzzleBoxTheme {
        NewPuzzleSheet(games = PreviewGames, onPick = { _, _ -> }, onDismiss = {})
    }
}

@PuzzleBoxPreviews
@Composable
private fun NewPuzzleSheetSizesPreview() {
    PuzzleBoxTheme {
        NewPuzzleSheet(games = PreviewGames, onPick = { _, _ -> }, onDismiss = {}, initialGame = GameId("queens"))
    }
}

/** The sheet's body alone: ModalBottomSheet draws in its own window, which some panes skip. */
@PuzzleBoxPreviews
@Composable
private fun NewPuzzleSheetContentPreview() {
    PuzzleBoxTheme {
        NewPuzzleSheetContent(games = PreviewGames, picked = null, onGame = {}, onBack = {}, onSize = {})
    }
}
