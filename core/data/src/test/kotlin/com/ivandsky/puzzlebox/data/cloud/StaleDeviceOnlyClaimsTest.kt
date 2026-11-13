package com.ivandsky.puzzlebox.data.cloud

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards against the claim that saved LinkedIn boards stay on the phone: they sync to the owner's cloud space when signed in. */
class StaleDeviceOnlyClaimsTest {

    private val stale = listOf(
        "Device-only: it lives in its own database file",
        "never reaches the leaderboard types or a backend",
        "nothing here leaves the phone",
    )

    @Test
    fun `no main source or doc says saved boards never leave the phone`() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }
        val files = root.walkTopDown()
            .onEnter { it.name != "build" && it.name != ".git" && it.name != ".gradle" && it.name != ".scratch" }
            .filter { it.isFile && (it.extension == "md" && it.path.contains("/docs/") || it.extension == "kt" && it.path.contains("/src/main/")) }
        val hits = files.flatMap { file ->
            val text = file.readText().replace(Regex("\\s*\n\\s*\\*?\\s*"), " ")
            stale.filter { text.contains(it) }.map { "${file.relativeTo(root)}: $it" }
        }.toList()
        assertTrue("stale device-only claims: $hits", hits.isEmpty())
    }
}
