package com.ivandsky.puzzlebox.model

/** The stored theme as read, or [ThemeMode.SYSTEM] with [failed] set when the read threw. */
data class ThemeRead(val mode: ThemeMode, val failed: Boolean = false)
