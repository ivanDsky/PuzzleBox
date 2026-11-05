package com.ivandsky.puzzlebox.designsystem

import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * The system's reduced-motion signal (design-direction §11, identity §2.6: every
 * MotionTokens job names its fallback, and every surface reads this one signal; the nav
 * shell gates all navigation motion on it, A20): animator scale 0 collapses pattern motion
 * (Zip next-target flash / hint-arrow fade, Queens mark fades, caption fades, sweep and
 * stamp) to instant state changes. Observed live, so flipping the
 * animator-scale setting while the app runs recomposes every reader. Guarded — preview /
 * layoutlib environments have no real settings table.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    val reduced = remember(resolver) { mutableStateOf(readReducedMotion(resolver)) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduced.value = readReducedMotion(resolver)
            }
        }
        val registered = runCatching {
            resolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
                false,
                observer,
            )
        }.isSuccess
        // A change between the first read and registration is not lost.
        reduced.value = readReducedMotion(resolver)
        onDispose { if (registered) resolver.unregisterContentObserver(observer) }
    }
    return reduced.value
}

private fun readReducedMotion(resolver: ContentResolver): Boolean = runCatching {
    Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
}.getOrDefault(false)
