package com.ivandsky.puzzlebox.app.shortcut

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Publishes the launcher menu once per app start when [enabled], else removes whatever an earlier run published.
 * The menu is a convenience: a shortcut manager failure is logged, never a reason to fail the start.
 */
class ShortcutAppHooks(
    private val enabled: Boolean,
    private val games: () -> List<ShortcutGame>,
    private val publisher: ShortcutPublisher,
    private val scope: CoroutineScope,
) {

    fun start() {
        scope.launch {
            try {
                if (enabled) publisher.publish(BuildShortcutEntries()(games())) else publisher.clear()
            } catch (e: Exception) {
                Log.w(TAG, "launcher shortcuts not updated: ${e.message}")
            }
        }
    }

    private companion object {
        const val TAG = "ShortcutAppHooks"
    }
}
