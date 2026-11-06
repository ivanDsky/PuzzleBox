package com.ivandsky.puzzlebox.domain

/**
 * A release's numeric MAJOR.MINOR.PATCH core (spec A3), ordered component by component so
 * 0.9.0 < 0.10.0. [parse] drops a leading `v` and any `-suffix` / `+build`.
 */
data class AppVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<AppVersion> {

    override fun compareTo(other: AppVersion): Int = compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })

    companion object {
        private val CORE = Regex("""v?(\d+)\.(\d+)\.(\d+)(?:[-+].*)?""")

        /** The version in [raw], or null when it is not a three-part numeric core. */
        fun parse(raw: String): AppVersion? {
            val (major, minor, patch) = CORE.matchEntire(raw)?.destructured ?: return null
            return AppVersion(
                major.toIntOrNull() ?: return null,
                minor.toIntOrNull() ?: return null,
                patch.toIntOrNull() ?: return null,
            )
        }
    }
}
