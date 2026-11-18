package com.ivandsky.puzzlebox.konsist

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A release bumps four places in one commit: PROFILE's version line, the Gradle versionName fallback, the
 * newest What's new entry and the newest release-history entry. This fails when one of them was left behind.
 */
class WhatsNewVersionTest {
    private val root = File(System.getProperty("puzzlebox.repoRoot"))

    private fun text(path: String): String = File(root, path).readText()

    private fun profileVersion(profile: String): String? =
        Regex("""(?m)^- version: (\S+)""").find(profile.substringAfter("## Version", ""))?.groupValues?.get(1)

    private fun gradleFallback(buildFile: String): String? =
        Regex("""gradleProperty\("agentVersionName"\)\.orNull \?: "([^"]+)"""").find(buildFile)?.groupValues?.get(1)

    private fun newestNotes(notes: String): String? =
        Regex("""(?m)^## (\S+) \d{4}-\d{2}-\d{2}""").find(notes)?.groupValues?.get(1)

    private fun newestHistory(history: String): String? =
        Regex("""(?m)^- \*\*([^*]+)\*\*:""").find(history)?.groupValues?.get(1)

    private fun disagreement(versions: Map<String, String?>): String? =
        if (versions.values.toSet().size == 1 && versions.values.first() != null) null
        else "version sources disagree: " + versions.entries.joinToString("; ") { (source, version) -> "$source = ${version ?: "not found"}" }

    @Test
    fun theVersionSourcesAgree() {
        val versions = mapOf(
            "PROFILE.md version" to profileVersion(text("PROFILE.md")),
            "app/build.gradle.kts agentVersionName fallback" to gradleFallback(text("app/build.gradle.kts")),
            "whats_new.txt newest entry" to newestNotes(text("feature/profile/impl/src/main/assets/whats_new.txt")),
            "docs/release-history.md newest entry" to newestHistory(text("docs/release-history.md")),
        )
        val message = disagreement(versions)
        assertTrue(message.orEmpty(), message == null)
    }

    @Test
    fun aMismatchOrAMissingVersionIsReportedWithEveryValue() {
        val stale = disagreement(mapOf("PROFILE" to "0.37.0", "gradle" to "0.36.2", "notes" to "0.36.2", "history" to null))
        assertEquals(
            "version sources disagree: PROFILE = 0.37.0; gradle = 0.36.2; notes = 0.36.2; history = not found",
            stale,
        )
        assertEquals(null, disagreement(mapOf("PROFILE" to "0.37.0", "gradle" to "0.37.0")))
        assertTrue(disagreement(mapOf("PROFILE" to null, "gradle" to null)) != null)
    }

    @Test
    fun eachSourceReadsItsVersionFromItsOwnFormat() {
        assertEquals("1.2.3", profileVersion("# P\n\n## Version\n- app-id: x\n- version: 1.2.3  # note\n"))
        assertEquals(null, profileVersion("- version: 9.9.9\n"))
        assertEquals("1.2.3", gradleFallback("""versionName = providers.gradleProperty("agentVersionName").orNull ?: "1.2.3""""))
        assertEquals("1.2.3", newestNotes("# c\n\n## 1.2.3 2026-11-18\n- a\n## 1.2.2 2026-11-01\n"))
        assertEquals("1.2.3", newestHistory("# Release history\n\nrule\n\n- **1.2.3**: x\n- **1.2.2**: y\n"))
    }
}
