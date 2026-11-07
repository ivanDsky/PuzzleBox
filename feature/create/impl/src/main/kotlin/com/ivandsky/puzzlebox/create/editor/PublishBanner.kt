package com.ivandsky.puzzlebox.create.editor

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.ivandsky.puzzlebox.designsystem.AppShapes
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.domain.PublishOutcome

/**
 * Why the last publish didn't land, above Publish: offline or refused in the error container
 * (Publish then reads Retry), the day's cap in the tertiary one. Any other [outcome] shows
 * nothing; shown, it keeps its own gap above Publish. The banner opens and closes in place (expand + fade on the enter curve); reduced
 * motion fades it.
 */
@Composable
internal fun PublishBanner(outcome: PublishOutcome?, modifier: Modifier = Modifier) {
    val reduced = rememberReducedMotion()
    AnimatedContent(
        targetState = bannerOf(outcome),
        modifier = modifier,
        transitionSpec = { if (reduced) reducedSwap() else expandSwap() },
        contentAlignment = Alignment.TopCenter,
        label = "publishBanner",
    ) { banner -> if (banner != null) Banner(banner) }
}

@Composable
private fun Banner(banner: Kind) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (banner) {
        Kind.Cap -> colors.tertiaryContainer to colors.onTertiaryContainer
        Kind.Offline, Kind.Rejected -> colors.errorContainer to colors.onErrorContainer
    }
    Surface(
        color = container,
        contentColor = content,
        shape = AppShapes.medium,
        modifier = Modifier
            .padding(bottom = Spacing.sm)
            .fillMaxWidth()
            .testTag(banner.tag)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(banner.title, style = MaterialTheme.typography.titleSmall)
            Text(banner.body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private enum class Kind(val tag: String, val title: String, val body: String) {
    Offline(
        tag = "create.publish.banner.offline",
        title = "You're offline — publishing needs a connection",
        body = "Your draft and test solve are saved on this phone. Retry when you're back online.",
    ),
    Cap(
        tag = "create.publish.banner.cap",
        title = "You've published 20 today",
        body = "That's today's limit; publishing opens again tomorrow. You can still test solve, and the draft waits here.",
    ),
    Rejected(
        tag = "create.publish.banner.rejected",
        title = "Couldn't publish",
        body = "The puzzle wasn't accepted this time. Your draft is saved; try again.",
    ),
}

private fun bannerOf(outcome: PublishOutcome?): Kind? = when (outcome) {
    PublishOutcome.Offline -> Kind.Offline
    PublishOutcome.DailyCapReached -> Kind.Cap
    is PublishOutcome.Rejected -> Kind.Rejected
    else -> null
}

private fun expandSwap(): ContentTransform =
    (fadeIn(tween(MotionTokens.EnterDuration, easing = MotionTokens.EasingEmphasizedDecelerate)) +
        expandVertically(tween(MotionTokens.EnterDuration, easing = MotionTokens.EasingEmphasizedDecelerate), expandFrom = Alignment.Top))
        .togetherWith(
            fadeOut(tween(MotionTokens.DurationShort, easing = MotionTokens.EasingEmphasizedAccelerate)) +
                shrinkVertically(tween(MotionTokens.DurationShort, easing = MotionTokens.EasingEmphasizedAccelerate), shrinkTowards = Alignment.Top),
        )

private fun reducedSwap(): ContentTransform =
    ContentTransform(fadeIn(tween(MotionTokens.ReducedFadeDuration)), fadeOut(tween(MotionTokens.ReducedFadeDuration)), sizeTransform = null)

@PuzzleBoxPreviews
@Composable
private fun PublishBannerOfflinePreview() {
    PuzzleBoxTheme { PublishBanner(PublishOutcome.Offline) }
}

@PuzzleBoxPreviews
@Composable
private fun PublishBannerCapPreview() {
    PuzzleBoxTheme { PublishBanner(PublishOutcome.DailyCapReached) }
}

@PuzzleBoxPreviews
@Composable
private fun PublishBannerRejectedPreview() {
    PuzzleBoxTheme { PublishBanner(PublishOutcome.Rejected("refused")) }
}
