package com.ivandsky.puzzlebox.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex

/**
 * Prototype 141 (throwaway): the pane beside a tab pager, changing in step with [link]. [kindOf] names what the pane
 * shows for a page (two pages of one kind share one pane, which then does not move); [content] draws a kind. The kind
 * of [TabSwipeLink.under] is drawn opaque beneath and the kind of [TabSwipeLink.over] on top of it on the screen's
 * background at [TabSwipeLink.overShare] (a crossfade), so the pane never shows through to the background mid-change.
 * Under [TabSwipeLink.through] it fades through instead: the kind beneath is gone by half the share and the one on
 * top comes in over the other half, so the two are never on show together. A kind keeps its composition while it
 * moves between the two layers, and is composed only while it shows.
 *
 * [content]'s `settled` is true for a kind composed while a change was in flight, until that change lands: it arrives
 * under the crossfade, so it should skip its own entrance.
 */
@Composable
fun <K : Any> TabSwipePane(
    link: TabSwipeLink,
    kindOf: (page: Int) -> K,
    modifier: Modifier = Modifier,
    content: @Composable (kind: K, settled: Boolean) -> Unit,
) {
    val latest by rememberUpdatedState(kindOf)
    val under by remember(link) { derivedStateOf { latest(link.under) } }
    val over by remember(link) { derivedStateOf { latest(link.over) } }
    val background = MaterialTheme.colorScheme.background
    Box(modifier) {
        for (kind in if (over == under) listOf(under) else listOf(under, over)) {
            key(kind) {
                val top = kind == over && kind != under
                val midChange = over != under
                val settled = remember { midChange } && midChange
                Box(
                    Modifier
                        .fillMaxSize()
                        .zIndex(if (top) 1f else 0f)
                        .graphicsLayer {
                            val share = link.overShare
                            alpha = when {
                                !midChange -> 1f
                                link.through -> (if (top) 2f * share - 1f else 1f - 2f * share).coerceAtLeast(0f)
                                top -> share
                                else -> 1f
                            }
                        }
                        .then(if (top) Modifier.background(background) else Modifier),
                ) { content(kind, settled) }
            }
        }
    }
}
