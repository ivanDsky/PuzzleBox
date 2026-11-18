package com.ivandsky.puzzlebox.profile.whatsnew

/**
 * Reads the bundled notes file: `## <semver> <yyyy-mm-dd>` opens a release, `- <text>` adds one note to it, `# ...`
 * comments and blank lines are skipped. Releases come back in file order; their order is not checked here, but a version
 * listed twice is an error (the page keys its cards by version).
 */
object WhatsNewParser {

    private val header = Regex("""## (\d+\.\d+\.\d+) ($ISO_DATE_PATTERN)""")

    /** @throws IllegalArgumentException with a message starting `whats_new line <n>:` for the first bad line. */
    fun parse(text: String): List<WhatsNewRelease> {
        val releases = mutableListOf<WhatsNewRelease>()
        var open: Open? = null
        val seen = mutableSetOf<String>()

        fun close() {
            val release = open ?: return
            if (release.notes.isEmpty()) fail(release.line, "release ${release.version} has no notes")
            releases += WhatsNewRelease(release.version, release.date, release.notes)
        }

        text.lines().forEachIndexed { index, raw ->
            val number = index + 1
            val line = raw.trimEnd()
            when {
                line.isEmpty() -> Unit
                line.startsWith("#") && !line.startsWith("##") -> Unit
                line.startsWith("##") -> {
                    val match = header.matchEntire(line) ?: fail(number, "expected `## <x.y.z> <yyyy-mm-dd>`, got `$line`")
                    if (!seen.add(match.groupValues[1])) fail(number, "duplicate release ${match.groupValues[1]}")
                    close()
                    open = Open(number, match.groupValues[1], match.groupValues[2])
                }
                line.startsWith("- ") -> {
                    val note = line.removePrefix("- ").trim()
                    val release = open ?: fail(number, "note before any release")
                    if (note.isEmpty()) fail(number, "empty note")
                    release.notes += note
                }
                else -> fail(number, "unknown line `$line`")
            }
        }
        close()
        return releases
    }

    private class Open(val line: Int, val version: String, val date: String) {
        val notes = mutableListOf<String>()
    }

    private fun fail(line: Int, reason: String): Nothing = throw IllegalArgumentException("whats_new line $line: $reason")
}
