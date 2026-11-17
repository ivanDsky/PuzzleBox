package com.ivandsky.puzzlebox.play

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.ivandsky.puzzlebox.gameui.LocalSolveRestOnBoard
import org.koin.compose.koinInject

/**
 * Whether a daily solve rests on its board before Result (M10-6): the Result moments flag as the play module read it,
 * so no composable reads the flag itself.
 */
internal class SolveRest(val enabled: Boolean)

/** Runs a daily board's [content] with [LocalSolveRestOnBoard] set from [SolveRest]; off, the board is as it was. */
@Composable
internal fun SolveRestHost(content: @Composable () -> Unit) {
    val rest = koinInject<SolveRest>()
    CompositionLocalProvider(LocalSolveRestOnBoard provides rest.enabled, content)
}
