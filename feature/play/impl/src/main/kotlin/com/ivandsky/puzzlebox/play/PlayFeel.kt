package com.ivandsky.puzzlebox.play

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.ivandsky.puzzlebox.gameui.LocalPlayFeel
import org.koin.compose.currentKoinScope

/**
 * The PlayFeel flag as feature/play's DI read it once per graph (spec A19). Every play board and the Library lessons
 * host hand it down through [PlayFeelHost]; no composable reads the flag itself.
 */
internal class PlayFeel(val enabled: Boolean)

/**
 * Provides [LocalPlayFeel] from the graph's [PlayFeel] around [content], with [ZipDotPulseHost] and
 * [HintPolishHost] inside: the one host every play board and lesson already wraps. A graph that binds none (an isolated
 * route test, a golden host) reads as off, as the locals' defaults do.
 */
@Composable
internal fun PlayFeelHost(content: @Composable () -> Unit) {
    val scope = currentKoinScope()
    val enabled = remember(scope) { scope.getOrNull<PlayFeel>()?.enabled == true }
    CompositionLocalProvider(LocalPlayFeel provides enabled) { ZipDotPulseHost { HintPolishHost(content) } }
}
