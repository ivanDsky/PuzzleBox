package com.ivandsky.puzzlebox.profile.whatsnew

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Gradle runs a module's unit tests in the module directory, so the asset is read by its source path.
class WhatsNewBundledTest {

    private val releases = WhatsNewParser.parse(File("src/main/assets/whats_new.txt").readText())

    @Test
    fun `the bundled file holds eighteen releases, newest is 0_40_0`() {
        assertEquals(18, releases.size)
        assertEquals("0.40.0", releases.first().version)
    }

    @Test
    fun `versions descend by semver, so a release added in the wrong place fails here`() {
        val versions = releases.map { it.version.split('.').map(String::toInt) }

        versions.zipWithNext().forEach { (newer, older) ->
            assertTrue("$newer must come before $older", compare(newer, older) > 0)
        }
    }

    @Test
    fun `every release has a note and no note carries an issue number`() {
        releases.forEach { release ->
            assertTrue("${release.version} has no notes", release.notes.isNotEmpty())
            release.notes.forEach { assertFalse("${release.version}: $it", '#' in it) }
        }
    }

    private fun compare(a: List<Int>, b: List<Int>): Int =
        a.zip(b).map { (x, y) -> x.compareTo(y) }.firstOrNull { it != 0 } ?: 0
}
