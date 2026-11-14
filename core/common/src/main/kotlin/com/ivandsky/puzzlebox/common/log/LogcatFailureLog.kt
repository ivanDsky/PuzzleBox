package com.ivandsky.puzzlebox.common.log

import android.util.Log

/** The logcat sink, the repo's `Log.w(TAG, …)` convention. */
object LogcatFailureLog : FailureLog {
    override fun warn(tag: String, message: String, cause: Throwable) {
        Log.w(tag, message, cause)
    }
}
