package com.ivandsky.puzzlebox.gameui

import com.ivandsky.puzzlebox.designsystem.AppTextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The hint affordance (F1 §3) — the CORE half of hints: the button and the running count.
 * The GAME half is what a press does ([PlayHints.requestHint]): Zip places a segment,
 * Tango reveals a forced move with its deduction, Queens runs a staged ladder. The count
 * is the session's [count] (A3: hints are counted by the game, score = time + hints), so
 * one user-visible hint action counts exactly once, including Zip's rescue flow where a
 * free trim precedes the step (F2 §10 ruling).
 */
@Composable
fun HintButton(
    count: Int,
    onHint: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    AppTextButton(
        text = if (count == 0) "Hint" else "Hint · $count",
        onClick = onHint,
        modifier = modifier,
        enabled = enabled,
    )
}
