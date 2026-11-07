package com.ivandsky.puzzlebox.create.editor

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PrimaryButton
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.domain.PublishOutcome
import com.ivandsky.puzzlebox.model.ShareCode
import kotlin.math.roundToInt

/**
 * The Studio's Publish between why the last publish fell short ([PublishBanner]) and its share
 * code. Enabled once the draft is [ready] and no publish runs; while one runs the pill folds into
 * a spinner circle announcing "Publishing". Signed out it reads "Sign in & publish": the tap signs
 * in first, then publishes. Offline or refused it reads "Retry publish" and, as the attempt
 * ends, jolts once on the reject spring; reduced motion leaves the jolt to the banner alone.
 */
@Composable
internal fun PublishCta(state: PublishSession.State, ready: Boolean, onPublish: () -> Unit, modifier: Modifier = Modifier) {
    val jolt = rememberRejectJolt(state)
    Column(modifier) {
        PublishBanner(state.outcome, Modifier.fillMaxWidth())
        PublishButton(state, ready, onPublish, jolt)
        state.outcome?.let { PublishedTicket(it, Modifier.padding(top = Spacing.sm)) }
    }
}

@Composable
private fun PublishButton(state: PublishSession.State, ready: Boolean, onPublish: () -> Unit, jolt: () -> Float) {
    PrimaryButton(
        text = when {
            state.busy -> "Publishing"
            state.retry -> "Retry publish"
            state.signedIn -> "Publish"
            else -> "Sign in & publish"
        },
        onClick = onPublish,
        enabled = ready && !state.busy,
        loading = state.busy,
        modifier = Modifier
            .fillMaxWidth()
            .offset { IntOffset(jolt().roundToInt(), 0) }
            .testTag("create.action.publish"),
    )
}

/**
 * The CTA's sideways offset in px: one reject-spring swing each time a publish ends in a retry.
 * Only a run seen ending counts, so a restored Studio holding an old failure stays still.
 */
@Composable
private fun rememberRejectJolt(state: PublishSession.State): () -> Float {
    val reduced = rememberReducedMotion()
    val kick = with(LocalDensity.current) { JoltKick.toPx() }
    val offset = remember { Animatable(0f) }
    var wasBusy by remember { mutableStateOf(state.busy) }
    LaunchedEffect(state.busy) {
        val ended = wasBusy && !state.busy
        wasBusy = state.busy
        if (!ended || !state.retry || reduced) return@LaunchedEffect
        offset.snapTo(0f)
        offset.animateTo(
            targetValue = 0f,
            animationSpec = spring(MotionTokens.RejectSpringDamping, MotionTokens.RejectSpringStiffness),
            initialVelocity = kick * JOLT_VELOCITY_PER_PX,
        )
    }
    return { offset.value }
}

/** How far the reject jolt first swings. */
private val JoltKick = 6.dp

/** Launch velocity per px of [JoltKick] (1/s), so the reject spring's first swing peaks near it. */
private const val JOLT_VELOCITY_PER_PX = 36f

@PuzzleBoxPreviews
@Composable
private fun PublishCtaPublishingPreview() {
    PuzzleBoxTheme { PublishCta(PublishSession.State(busy = true), ready = true, onPublish = {}) }
}

@PuzzleBoxPreviews
@Composable
private fun PublishCtaOfflinePreview() {
    PuzzleBoxTheme { PublishCta(PublishSession.State(outcome = PublishOutcome.Offline), ready = true, onPublish = {}) }
}

@PuzzleBoxPreviews
@Composable
private fun PublishCtaCapPreview() {
    PuzzleBoxTheme { PublishCta(PublishSession.State(outcome = PublishOutcome.DailyCapReached), ready = false, onPublish = {}) }
}

@PuzzleBoxPreviews
@Composable
private fun PublishCtaPublishedPreview() {
    PuzzleBoxTheme {
        PublishCta(PublishSession.State(outcome = PublishOutcome.Published(checkNotNull(ShareCode.parse("K7Q2MX")))), ready = true, onPublish = {})
    }
}
