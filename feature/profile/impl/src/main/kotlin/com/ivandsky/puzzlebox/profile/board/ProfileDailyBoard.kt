package com.ivandsky.puzzlebox.profile.board

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.app.screens.ProfileUiState
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.gameui.GameFilterChip
import com.ivandsky.puzzlebox.gameui.board.DailyBoard
import com.ivandsky.puzzlebox.model.GameId

/**
 * Flag-on Profile board (A2, A4): one [GameFilterChip] per game in game order over the shared
 * [DailyBoard] of the selected game; switching crossfades, and cuts under reduced motion (A18).
 * While [ProfileUiState.PerGameBoard.selected] is null the board is Loading under no chip.
 */
@Composable
internal fun ProfileDailyBoard(
    games: List<ProfileUiState.GameRow>,
    board: ProfileUiState.PerGameBoard,
    onSelect: (GameId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = rememberReducedMotion()
    Column(
        modifier = modifier.fillMaxWidth().testTag("profile-board"),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            items(games, key = { it.gameId.value }) { game ->
                GameFilterChip(
                    brand = game.brand,
                    label = game.name,
                    selected = board.selected == game.gameId,
                    onClick = { onSelect(game.gameId) },
                    modifier = Modifier.testTag("profile-board-chip-${game.gameId.value}"),
                )
            }
        }
        AnimatedContent(
            targetState = board,
            transitionSpec = {
                if (reduced) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else {
                    val spec = tween<Float>(MotionTokens.DurationMedium, easing = MotionTokens.EasingStandard)
                    fadeIn(spec) togetherWith fadeOut(spec)
                }
            },
            contentKey = { it.selected },
            label = "profileBoard",
        ) { shown ->
            val name = games.firstOrNull { it.gameId == shown.selected }?.name
            DailyBoard(
                title = if (name != null) "$name · today" else "Today",
                order = shown.order,
                state = shown.state,
            )
        }
    }
}
