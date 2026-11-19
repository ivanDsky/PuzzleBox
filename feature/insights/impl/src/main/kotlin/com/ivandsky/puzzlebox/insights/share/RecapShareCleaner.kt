package com.ivandsky.puzzlebox.insights.share

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Removes the recap image the last share left in the cache. The app calls [start] once when it starts, so a recap
 * image does not outlive the session that shared it.
 */
class RecapShareCleaner internal constructor(
    private val renderer: RecapShareRenderer,
    private val scope: CoroutineScope,
) {

    fun start() {
        scope.launch { renderer.clear() }
    }
}
