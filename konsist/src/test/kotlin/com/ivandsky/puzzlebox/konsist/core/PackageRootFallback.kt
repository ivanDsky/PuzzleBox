package com.ivandsky.puzzlebox.konsist.core

object PackageRootFallback {
    private const val ROOT = "com.ivandsky.puzzlebox"

    fun forJvmModule(gradlePath: String): String? {
        val segments = gradlePath.removePrefix(":").split(":")
        if (segments.size != 2) return null
        val name = segments[1].replace("-", "")
        return when (segments[0]) {
            "core" -> "$ROOT.$name"
            "games" -> "$ROOT.games.$name"
            else -> null
        }
    }
}
