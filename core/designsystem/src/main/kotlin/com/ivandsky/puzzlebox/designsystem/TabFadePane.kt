package com.ivandsky.puzzlebox.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * What sits beside an [UnderlineTabPager] and changes with it, on the same [link]: the tablet's right pane. [content]
 * draws the pane for tab `page`. Only the pages a change is on or between are composed, so at rest it is the one tab's
 * pane and nothing of the others is in the tree; each fades by [TabFadeLink.shown] in place, so the pane is empty at
 * half way and its title never shows over another's.
 *
 * A pane that composes while its tab is fading in starts settled ([LocalTabFadingIn]): the fade is its entrance.
 */
@Composable
fun TabFadePane(link: TabFadeLink, modifier: Modifier = Modifier, content: @Composable (page: Int) -> Unit) {
    val live by remember(link) { derivedStateOf(structuralEqualityPolicy()) { link.live() } }
    Box(modifier) {
        for (page in live) {
            key(page) {
                val fadingIn = remember { link.changing }
                CompositionLocalProvider(LocalTabFadingIn provides fadingIn) {
                    Box(Modifier.fillMaxSize().graphicsLayer { alpha = link.shown(page) }) { content(page) }
                }
            }
        }
    }
}
