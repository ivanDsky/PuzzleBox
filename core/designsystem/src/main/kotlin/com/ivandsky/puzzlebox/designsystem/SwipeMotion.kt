package com.ivandsky.puzzlebox.designsystem

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Swipe (the Ranks hub's boards): the finger scrubs 1:1; a release settles on a soft, critically damped spring
// (no wobble). A drag commits at CommitFraction of the board's width, never further than CommitCap, or at a
// flick of FlingVelocity a second. Past the first or last board the content follows the finger by EdgeFollow
// of the way. Reduced: no scrub, the board steps on release. A settle is a fraction of a whole board's way, so
// it lands within SettleThreshold of it: a distance nobody sees, where the default would end on a visible step.
object SwipeMotion {
    const val SettleStiffness = Spring.StiffnessLow
    const val SettleThreshold = 0.002f
    val Settle: SpringSpec<Float> = spring(MotionTokens.SpringDampingDefault, SettleStiffness, SettleThreshold)
    const val CommitFraction = 0.25f
    val CommitCap: Dp = 120.dp
    val FlingVelocity: Dp = 300.dp
    const val EdgeFollow = 0.2f
}
