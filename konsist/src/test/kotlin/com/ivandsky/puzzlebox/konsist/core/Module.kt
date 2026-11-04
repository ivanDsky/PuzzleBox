package com.ivandsky.puzzlebox.konsist.core

/** path = Gradle path (":games:zip"), dir = root-relative dir ("games/zip"), plugins = ids per BuildFileReader. */
data class Module(val path: String, val dir: String, val packageRoot: String, val plugins: Set<String>)
