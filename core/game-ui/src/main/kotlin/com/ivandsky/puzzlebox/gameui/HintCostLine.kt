package com.ivandsky.puzzlebox.gameui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/** The one-time second caption line (spec A11). */
const val HINT_COST_TEXT = "Hints are counted on your result"

/**
 * The hint cost line a route offers. [seen] is whether this game has ever shown it, [inputs] counts the player's inputs
 * (the line clears on the next one), and [markSeen] records that the line has shown. [seen] and [inputs] read state, so
 * one instance serves the whole screen. Only routes that record a result provide one; no provider (the sandbox,
 * lessons) never shows the line.
 */
class HintCostLine(val seen: () -> Boolean, val inputs: () -> Int, val markSeen: () -> Unit)

val LocalHintCostLine: ProvidableCompositionLocal<HintCostLine?> = staticCompositionLocalOf { null }

private const val NONE = -1

/**
 * The cost line [PlayChrome]'s caption slot offers right now, or null. It arms when a hint applies ([PlayHints.hintsUsed]
 * rises, so a tap on a disabled Hint never counts) under an unseen [LocalHintCostLine], and stays
 * until the player's next input. The count a session starts from is taken once the hint is enabled, which a game
 * only is after its session is loaded, so a restored session's earlier hints never arm it. The slot calls
 * [HintCostLine.markSeen] when it actually draws the line.
 */
@Composable
internal fun hintCostNote(hints: PlayHints?): String? {
    val line = LocalHintCostLine.current
    val offered by rememberUpdatedState(line?.takeIf { !it.seen() })
    val used = hints?.hintsUsed ?: 0
    val enabled = hints?.hintEnabled == true
    var baseline by remember(hints) { mutableIntStateOf(NONE) }
    // Not saveable: a restored process counts inputs from 0 again, so a saved mark could re-arm the line.
    var armedInputs by remember { mutableIntStateOf(NONE) }
    val applying = offered != null && baseline != NONE && used > baseline
    LaunchedEffect(used, enabled) {
        val now = offered
        when {
            baseline == NONE -> if (enabled) baseline = used
            used > baseline && now != null -> {
                armedInputs = now.inputs()
                baseline = used
            }
            else -> baseline = used
        }
    }
    val armed = armedInputs != NONE && line != null && line.inputs() == armedInputs
    return HINT_COST_TEXT.takeIf { applying || armed }
}
