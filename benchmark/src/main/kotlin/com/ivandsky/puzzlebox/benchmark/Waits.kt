package com.ivandsky.puzzlebox.benchmark

import android.os.SystemClock
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
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

/** Taps the first match of [selector] once it is on screen. */
internal fun MacrobenchmarkScope.tap(selector: BySelector, timeoutMs: Long = WAIT_MS) {
    waitFor(selector, timeoutMs).click()
}

/** True when [selector] matches now; no waiting. */
internal fun MacrobenchmarkScope.isShown(selector: BySelector): Boolean = device.hasObject(selector)

/** Polls [find] until it returns non-null, or fails after [timeoutMs]. */
internal fun <T : Any> poll(what: String, timeoutMs: Long = WAIT_MS, find: () -> T?): T {
    val deadline = SystemClock.elapsedRealtime() + timeoutMs
    while (true) {
        find()?.let { return it }
        check(SystemClock.elapsedRealtime() < deadline) { "Not on screen within ${timeoutMs}ms: $what" }
        SystemClock.sleep(POLL_MS)
    }
}
