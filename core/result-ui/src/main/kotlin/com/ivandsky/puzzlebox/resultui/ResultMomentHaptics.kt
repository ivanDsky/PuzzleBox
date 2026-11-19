package com.ivandsky.puzzlebox.resultui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.ivandsky.puzzlebox.designsystem.ResultBeats
import com.ivandsky.puzzlebox.designsystem.beatDelayMs
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.domain.moments.Moment
import kotlinx.coroutines.delay

/**
 * The one buzz of a Result (L1), by its strongest moment, in the duel reveal's vocabulary: the strongest,
 * [HapticFeedbackType.LongPress], for a record (a personal best, a first sub-X); [HapticFeedbackType.Confirm] for a
 * place among friends; the soft [HapticFeedbackType.TextHandleMove] tick for a full set and a streak.
 */
internal object ResultMomentHaptics {

    fun typeFor(moment: Moment): HapticFeedbackType = when (moment) {
        Moment.PersonalBest, is Moment.FirstSubX -> HapticFeedbackType.LongPress
        is Moment.TopThreeFriends, is Moment.PassedFriend -> HapticFeedbackType.Confirm
        is Moment.FullSetDone, is Moment.Streak -> HapticFeedbackType.TextHandleMove
    }
}

/**
 * Buzzes for [strongest] once per Result: as the moment card lands when a moment is there on the first draw, at once
 * when the first one arrives later (a friend-board moment), and never again, whatever is added after. Nothing
 * buzzes without a moment. Reduced motion keeps the buzz (it only drops the wait for the card).
 */
@Composable
internal fun ResultMomentHapticsEffect(strongest: Moment?) {
    val haptics = LocalHapticFeedback.current
    val reduced = rememberReducedMotion()
    val onFirstDraw = remember { strongest != null }
    var buzzed by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(strongest, reduced) {
        if (buzzed || strongest == null) return@LaunchedEffect
        if (onFirstDraw) delay(beatDelayMs(ResultBeats.Blocks, reduced = reduced).toLong())
        buzzed = true
        haptics.performHapticFeedback(ResultMomentHaptics.typeFor(strongest))
    }
}
