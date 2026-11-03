package com.ivandsky.puzzlebox.games.tangoui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import com.ivandsky.puzzlebox.designsystem.MotionPattern
import com.ivandsky.puzzlebox.designsystem.MotionTokens

/**
 * Tango motion (design-direction §7): named MotionPatterns only, exit faster than
 * enter, reduced-motion renders everything instant (no scale pulse, instant captions).
 */
val ViolationFlash = MotionPattern(
    name = "ViolationFlash",
    description = "Tango violation entrance: cell scale pulse + dashed border + tint; " +
        "persistent until the board is legal again; caption fades in",
    enterDurationMillis = MotionTokens.DurationShort, // 120 ms caption fade
    enterEasing = MotionTokens.EasingEmphasizedDecelerate,
    exitDurationMillis = MotionTokens.NavTransitionExit, // 90 ms — exits faster than enter
    exitEasing = MotionTokens.EasingEmphasizedAccelerate,
)

// ViolationFlash cell-scale retired (user QA 2026-11-02): 1→0.86 read as a
// permanently shrunken glyph. Violations carry through tint + dashed border.

/** Caption fade-in duration for both ViolationFlash and HintReveal (§7). */
val captionFadeMillis = MotionTokens.DurationShort

/** Caption fade-out — faster than the entrance (§7: exit faster than enter). */
val captionFadeOutMillis = MotionTokens.NavTransitionExit
