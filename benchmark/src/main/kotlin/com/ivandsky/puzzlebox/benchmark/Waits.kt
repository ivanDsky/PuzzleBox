package com.ivandsky.puzzlebox.benchmark

import android.os.SystemClock
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until

/** Long enough for a slow or absent network; a journey never waits on data it cannot get. */
internal const val WAIT_MS = 10_000L

private const val POLL_MS = 25L

/** The first match of [selector] within [timeoutMs], or a failure naming it. */
internal fun MacrobenchmarkScope.waitFor(selector: BySelector, timeoutMs: Long = WAIT_MS): UiObject2 =
    device.wait(Until.findObject(selector), timeoutMs)
        ?: error("Not on screen within ${timeoutMs}ms: $selector; on screen: ${onScreen()}")

/** The texts and descriptions on screen, for a failure message: what a journey saw instead. */
private fun MacrobenchmarkScope.onScreen(): String = runCatching {
    device.findObjects(By.pkg(TARGET_PACKAGE))
        .mapNotNull { it.text ?: it.contentDescription }
        .joinToString(" | ")
}.getOrElse { "(screen unreadable: ${it.javaClass.simpleName})" }

/**
 * Taps the first match of [selector] once it is on screen. The object is found and clicked in one
 * step and found again when a recomposition or a transition swaps the node under the click
 * ([StaleObjectException]); a reference held across a wait goes stale on a screen that keeps
 * redrawing, such as a running board.
 */
internal fun MacrobenchmarkScope.tap(selector: BySelector, timeoutMs: Long = WAIT_MS) {
    poll("$selector", timeoutMs) { device.findObject(selector)?.also { it.click() } }
}

/** True when [selector] matches now; no waiting. */
internal fun MacrobenchmarkScope.isShown(selector: BySelector): Boolean = device.hasObject(selector)

/**
 * Polls [find] until it returns non-null, or fails after [timeoutMs] naming [what] and what was on
 * screen. A [StaleObjectException] from [find] is a node swapped mid-read: the next poll finds it again.
 */
internal fun <T : Any> MacrobenchmarkScope.poll(what: String, timeoutMs: Long = WAIT_MS, find: () -> T?): T {
    val deadline = SystemClock.elapsedRealtime() + timeoutMs
    while (true) {
        try {
            find()?.let { return it }
        } catch (_: StaleObjectException) {
            // Found again on the next poll.
        }
        check(SystemClock.elapsedRealtime() < deadline) { "Not on screen within ${timeoutMs}ms: $what; on screen: ${onScreen()}" }
        SystemClock.sleep(POLL_MS)
    }
}
