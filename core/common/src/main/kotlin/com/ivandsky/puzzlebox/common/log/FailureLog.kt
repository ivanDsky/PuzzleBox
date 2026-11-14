package com.ivandsky.puzzlebox.common.log

/** Where a boundary that swallows a failure reports its cause. Android-free modules take this instead of touching logcat. */
fun interface FailureLog {
    fun warn(tag: String, message: String, cause: Throwable)
}
