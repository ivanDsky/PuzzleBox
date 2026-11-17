package com.ivandsky.puzzlebox.gameui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.delay

/** The one-time second caption line (spec A11). */
const val HINT_COST_TEXT = "Hints are counted on your result"

/** How long the line stays when the hint has no caption of its own to clear it (a Spotlight game's hint). */
private const val DWELL_MS = 6_000L

/**
 * The hint cost line a route offers: [seen] is whether this game has ever shown it, [markSeen] records that it now has.
 * Only routes that record a result provide one; no provider (the sandbox, lessons) never shows the line.
 */
class HintCostLine(val seen: Boolean, val markSeen: () -> Unit)

val LocalHintCostLine: ProvidableCompositionLocal<HintCostLine?> = staticCompositionLocalOf { null }

/**
 * The cost line [PlayChrome]'s caption slot shows right now, or null. It shows when a hint applies ([PlayHints.hintsUsed]
 * rises, so a tap on a disabled Hint never counts) under [LocalPlayFeel] and an unseen [LocalHintCostLine], and calls
 * [HintCostLine.markSeen] then, not on the tap. It clears with the hint's [caption] on the next move, or after
 * [DWELL_MS] when the hint had none. A session restored with hints used does not count as a hint applying.
 */
@Composable
internal fun hintCostNote(hints: PlayHints?, caption: String?): String? {
    val line by rememberUpdatedState(LocalHintCostLine.current?.takeIf { LocalPlayFeel.current && !it.seen })
    val used = hints?.hintsUsed ?: 0
    var baseline by remember { mutableIntStateOf(used) }
    var shown by remember { mutableStateOf(false) }
    var sawCaption by remember { mutableStateOf(false) }
    LaunchedEffect(used) {
        val offered = line
        if (used > baseline && offered != null) {
            shown = true
            offered.markSeen()
        }
        baseline = used
    }
    LaunchedEffect(shown, caption) {
        when {
            !shown -> sawCaption = false
            caption != null -> sawCaption = true
            sawCaption -> shown = false
            else -> {
                delay(DWELL_MS)
                shown = false
            }
        }
    }
    return HINT_COST_TEXT.takeIf { shown }
}
