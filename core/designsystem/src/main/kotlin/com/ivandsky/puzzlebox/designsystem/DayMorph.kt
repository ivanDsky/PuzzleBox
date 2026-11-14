package com.ivandsky.puzzlebox.designsystem

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * [content] of [value]; a value that differs between two days crossfades over the old one in place on the day morph's
 * timing ([MotionTokens.DayMorphOut], [MotionTokens.DayMorphIn], [MotionTokens.DayMorphDelay]). Nothing travels or
 * clips, and the size is the new value's at once; [alignment] is where the leaving one sits in it. Reduced motion
 * swaps at once.
 */
@Composable
fun <T> DayMorph(
    value: T,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.TopStart,
    content: @Composable (T) -> Unit,
) {
    val reduced = rememberReducedMotion()
    AnimatedContent(
        targetState = value,
        modifier = modifier,
        transitionSpec = {
            if (reduced) {
                EnterTransition.None togetherWith ExitTransition.None using null
            } else {
                fadeIn(tween(MotionTokens.DayMorphIn, MotionTokens.DayMorphDelay, MotionTokens.EasingEase)) togetherWith
                    fadeOut(tween(MotionTokens.DayMorphOut, easing = MotionTokens.EasingEase)) using null
            }
        },
        contentAlignment = alignment,
        label = "dayMorph",
    ) { content(it) }
}

// Preview — Studio tooling only. The morph at rest (a preview never animates): one day's title.
@PuzzleBoxPreviews
@Composable
internal fun DayMorphPreview() {
    PuzzleBoxTheme {
        DayMorph("Zip · today", Modifier.padding(Spacing.md)) { Text(it, style = MaterialTheme.typography.titleMedium) }
    }
}
