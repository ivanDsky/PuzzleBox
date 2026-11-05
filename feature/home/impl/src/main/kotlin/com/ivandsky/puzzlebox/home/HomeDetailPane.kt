package com.ivandsky.puzzlebox.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PrimaryButton
import com.ivandsky.puzzlebox.designsystem.PuzzleBoard
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.domain.GameSummary

/** Expanded-width detail pane: today's preview until a history day is selected. */
@Composable
internal fun DetailPane(
    selected: HistoryDay?,
    game: GameSummary,
    resumeFeatured: Boolean,
    onPlayToday: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = selected,
        modifier = modifier
            .testTag("home.detail_pane"),
        // Shared axis (A12): arriving content decelerates in with a slight rise,
        // leaving content accelerates out — exit is the faster half.
        transitionSpec = {
            (
                fadeIn(tween(MotionTokens.DurationMedium, easing = MotionTokens.EasingEmphasizedDecelerate)) +
                    slideInHorizontally(tween(MotionTokens.DurationMedium, easing = MotionTokens.EasingEmphasizedDecelerate)) { it / 12 }
                )
                .togetherWith(fadeOut(tween(MotionTokens.DurationShort, easing = MotionTokens.EasingEmphasizedAccelerate)))
        },
        label = "home-detail",
        contentKey = { it?.isoDay },
    ) { day ->
        if (day == null) {
            TodayDetail(game = game, resume = resumeFeatured, onPlayToday = onPlayToday, modifier = Modifier.fillMaxSize())
        } else {
            DayDetail(day = day, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun TodayDetail(
    game: GameSummary,
    resume: Boolean,
    onPlayToday: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Spacer(Modifier.weight(1f))
        ZipPreviewBoard(modifier = Modifier.widthIn(max = 280.dp).fillMaxWidth())
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = "Today's ${game.displayName}",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "A fresh grid every day. Fill the path, check your time, keep your streak.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.xl),
            )
        }
        PrimaryButton(text = playTodayLabel(resume), onClick = onPlayToday)
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun DayDetail(
    day: HistoryDay,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Spacer(Modifier.weight(1f))
        Text(
            text = day.headline,
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Daily ${day.gameName}",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "Replay this day from Your history — every captured day stays playable.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = Spacing.xl),
        )
        Spacer(Modifier.weight(1f))
    }
}

/** Decorative Zip board: a traced path with numbered endpoints, no gestures. */
@Composable
private fun ZipPreviewBoard(modifier: Modifier = Modifier) {
    val path = remember {
        // Ticket 11: Zip's rule is that every cell is visited, so the decorative
        // board is a 5x5 Hamiltonian path — row snake on top, then a vertical comb
        // through the bottom three rows so the rhythm doesn't read mechanical.
        listOf(
            0 to 0, 0 to 1, 0 to 2, 0 to 3, 0 to 4,
            1 to 4, 1 to 3, 1 to 2, 1 to 1, 1 to 0,
            2 to 0, 3 to 0, 4 to 0, 4 to 1, 3 to 1,
            2 to 1, 2 to 2, 3 to 2, 4 to 2, 4 to 3,
            3 to 3, 2 to 3, 2 to 4, 3 to 4, 4 to 4,
        )
    }
    val scheme = MaterialTheme.colorScheme
    PuzzleBoard(
        cellsPerSide = 5,
        modifier = modifier.semantics { contentDescription = "Zip board preview" },
        cellContent = { row, col ->
            val index = path.indexOf(row to col)
            if (index >= 0) {
                val from = path.getOrNull(index - 1)
                val to = path.getOrNull(index + 1)
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(1.dp)
                        .background(
                            color = scheme.primary.copy(alpha = 0.12f),
                            shape = MaterialTheme.shapes.extraSmall,
                        ),
                )
                Canvas(Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2, size.height / 2)
                    val strokeWidthPx = size.width * 0.16f
                    listOfNotNull(from, to).forEach { (r, c) ->
                        val target = Offset(
                            center.x + (c - col) * size.width,
                            center.y + (r - row) * size.height,
                        )
                        drawLine(scheme.primary, center, target, strokeWidthPx, StrokeCap.Round)
                    }
                }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (index == 0 || index == path.lastIndex) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(scheme.primary, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (index == 0) "1" else "${path.size}",
                                style = MaterialTheme.typography.labelMedium,
                                color = scheme.onPrimary,
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(scheme.primary, CircleShape),
                        )
                    }
                }
            }
        },
    )
}
