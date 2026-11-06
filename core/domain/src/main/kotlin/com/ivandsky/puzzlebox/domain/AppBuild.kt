package com.ivandsky.puzzlebox.domain

/** Facts about the running build, bound by the app shell. */
data class AppBuild(val versionName: String, val checksForUpdates: Boolean)
