package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PrimaryButton
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.SecondaryButton
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.designsystem.skeleton
import kotlin.math.PI
import kotlin.math.sin

/**
 * The story's loading placeholder: seven segments, two text bars, one large block and two caption bars where the first
 * card will be dealt, shimmering together. A screen reader reads it as one "Loading your recap".
 */
@Composable
internal fun RecapLoadingSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier
            .clearAndSetSemantics {
                testTag = "recap.loading"
                contentDescription = "Loading your recap"
            }
            .padding(horizontal = Spacing.md)
            .padding(top = Spacing.md, bottom = Spacing.md),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            repeat(SegmentCount) { Bone(Modifier.weight(1f).height(SegmentHeight), CircleShape, strong = true) }
        }
        Column(Modifier.weight(1f).padding(horizontal = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            Spacer(Modifier.height(TopGap - Spacing.lg))
            Bone(Modifier.fillMaxWidth(TitleBarWidth).height(TextBarHeight), BarShape)
            Bone(Modifier.fillMaxWidth(SubtitleBarWidth).height(TextBarHeight), BarShape)
            Bone(Modifier.weight(1f).fillMaxWidth(), BlockShape)
            Bone(Modifier.fillMaxWidth(CaptionBarWidth).height(CaptionBarHeight), CircleShape)
            Bone(Modifier.fillMaxWidth(ShortCaptionBarWidth).height(CaptionBarHeight), CircleShape)
        }
    }
}

/** The tablet fact pane while loading: four empty rows that stand in for the facts, as tall as a short and a long fact. */
@Composable
internal fun RecapFactSkeleton(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        FactBoneHeights.forEach { height -> Bone(Modifier.fillMaxWidth().height(height), FactShape) }
    }
}

@Composable
private fun Bone(modifier: Modifier, shape: Shape, strong: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Box(modifier.skeleton(if (strong) colors.surfaceContainerHighest else colors.surfaceContainerHigh, shape))
}

/**
 * A recap that could not open: a "!" mark in a circle that shakes once, what happened, and [onRetry] and [onClose] as
 * two pills at least 48dp tall. It is centred in whatever space it is given. Under reduced motion the mark stays still.
 */
@Composable
internal fun RecapErrorFace(onRetry: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val reduced = rememberReducedMotion()
    val shake = remember { Animatable(0f) }
    LaunchedEffect(reduced) {
        if (!reduced) shake.animateTo(1f, tween(ShakeDuration, easing = MotionTokens.EasingLinear))
    }
    Column(
        modifier.fillMaxSize().padding(Spacing.xl).testTag("recap.error"),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .offset(x = ShakeTravel * (sin(ShakeWaves * PI * shake.value) * (1 - shake.value)).toFloat())
                .size(MarkSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.errorContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "!",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
        Spacer(Modifier.height(Spacing.lg))
        Text(
            text = "Couldn’t open your recap",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = "Your solves are safe. Try again in a moment.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.lg))
        PrimaryButton("Try again", onRetry, Modifier.width(ButtonWidth).testTag("recap.error.retry"))
        Spacer(Modifier.height(Spacing.md))
        SecondaryButton("Close", onClose, Modifier.width(ButtonWidth).testTag("recap.close"))
    }
}

private const val SegmentCount = 7
private val SegmentHeight = 4.dp
private val TopGap = 74.dp
private val TextBarHeight = 56.dp
private val CaptionBarHeight = 20.dp
private const val TitleBarWidth = 0.7f
private const val SubtitleBarWidth = 0.46f
private const val CaptionBarWidth = 0.8f
private const val ShortCaptionBarWidth = 0.55f
private val BarShape = RoundedCornerShape(12.dp)
private val BlockShape = RoundedCornerShape(28.dp)
private val FactShape = RoundedCornerShape(24.dp)
private val FactBoneHeights = listOf(200.dp, 132.dp, 112.dp, 132.dp)

/** The mark's shake: [ShakeDuration] ms linear, [ShakeTravel] × sin([ShakeWaves]·π·p) × (1 − p), as the motion contract says. */
private const val ShakeDuration = 320
private val ShakeTravel = 6.dp
private const val ShakeWaves = 6
private val MarkSize = 72.dp
private val ButtonWidth = 200.dp

@PuzzleBoxPreviews
@Composable
internal fun RecapLoadingSkeletonPreview() {
    PuzzleBoxTheme { RecapLoadingSkeleton(Modifier.fillMaxSize()) }
}

@PuzzleBoxPreviews
@Composable
internal fun RecapErrorFacePreview() {
    PuzzleBoxTheme { RecapErrorFace(onRetry = {}, onClose = {}) }
}
