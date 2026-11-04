package com.ivandsky.puzzlebox.konsist.core

sealed interface Finding {
    val line: Int?
    val text: String

    data class New(val violation: Violation) : Finding {
        override val line: Int? get() = null
        override val text: String
            get() = "new violation: ${violation.path}${violation.value?.let { " at $it" }.orEmpty()} (${violation.hint})"
    }

    data class Grown(val entry: AllowlistEntry, val violation: Violation) : Finding {
        override val line: Int? get() = entry.line
        override val text: String
            get() = "${entry.path} grew to ${violation.value} past ceiling ${entry.ceiling} (${violation.hint})"
    }

    data class Slack(val entry: AllowlistEntry, val violation: Violation) : Finding {
        override val line: Int? get() = entry.line
        override val text: String
            get() = "${entry.path} shrank to ${violation.value}; lower ceiling ${entry.ceiling}"
    }

    data class Stale(val entry: AllowlistEntry, val reason: String) : Finding {
        override val line: Int? get() = entry.line
        override val text: String get() = "${entry.path} is stale: $reason"
    }

    data class Malformed(override val line: Int, val raw: String, val reason: String) : Finding {
        override val text: String get() = "malformed entry '$raw': $reason"
    }

    data class Duplicate(override val line: Int, val firstLine: Int, val path: String) : Finding {
        override val text: String get() = "duplicate entry $path (first on line $firstLine)"
    }
}
