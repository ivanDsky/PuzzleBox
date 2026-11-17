package com.ivandsky.puzzlebox.play

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.ivandsky.puzzlebox.gameui.LocalZipDotPulse
import org.koin.compose.currentKoinScope

/**
 * The ZipDotPulse flag as feature/play's DI read it once per graph (backlog #1151). [ZipDotPulseHost] hands it to the
 * Zip board as LocalZipDotPulse; no composable reads the flag itself.
 */
internal class ZipDotPulse(val enabled: Boolean)

/**
 * Provides [LocalZipDotPulse] from the graph's [ZipDotPulse] around [content]: [PlayFeelHost] does for every play board
 * and lesson, and the replay screens wrap themselves in it too. A graph that binds none reads as off.
 */
@Composable
internal fun ZipDotPulseHost(content: @Composable () -> Unit) {
    val scope = currentKoinScope()
    val enabled = remember(scope) { scope.getOrNull<ZipDotPulse>()?.enabled == true }
    CompositionLocalProvider(LocalZipDotPulse provides enabled, content = content)
}
