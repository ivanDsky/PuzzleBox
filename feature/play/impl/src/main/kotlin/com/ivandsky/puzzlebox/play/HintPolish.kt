package com.ivandsky.puzzlebox.play

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.ivandsky.puzzlebox.gameui.LocalHintPolish
import org.koin.compose.currentKoinScope

/** The HintPolish flag as feature/play's DI read it once per graph (backlog #1172); no composable reads the flag itself. */
internal class HintPolish(val enabled: Boolean)

/**
 * Provides [LocalHintPolish] from the graph's [HintPolish] around [content]. A graph that binds none (an isolated route
 * test, a golden host) reads as off, as [LocalHintPolish]'s default does.
 */
@Composable
internal fun HintPolishHost(content: @Composable () -> Unit) {
    val scope = currentKoinScope()
    val enabled = remember(scope) { scope.getOrNull<HintPolish>()?.enabled == true }
    CompositionLocalProvider(LocalHintPolish provides enabled, content)
}
