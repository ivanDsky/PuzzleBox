package com.ivandsky.puzzlebox.play

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.gameui.LocalPlayTuning
import com.ivandsky.puzzlebox.gameui.PlayTuning
import com.ivandsky.puzzlebox.gameui.PlayTuningSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.koin.compose.currentKoinScope

/**
 * Provides the graph's [PlayTuningSource] tuning as [LocalPlayTuning] around [content], live, and hands the same
 * value to [content]. Null is today's layout. The source decides when the tuning exists (the app's binding holds
 * it behind the BoardErgonomics flag, spec A2); this never reads a flag. A graph that binds no source (an isolated
 * route test, a preview host) reads as null, as [LocalPlayTuning]'s default does.
 */
@Composable
internal fun PlayTuningHost(content: @Composable (tuning: PlayTuning?) -> Unit) {
    val scope = currentKoinScope()
    val source = remember(scope) { scope.getOrNull<PlayTuningSource>() }
    val tuning by (source?.tuning ?: Unbound).collectAsStateWithLifecycle()
    CompositionLocalProvider(LocalPlayTuning provides tuning) { content(tuning) }
}

private val Unbound: StateFlow<PlayTuning?> = MutableStateFlow(null)
