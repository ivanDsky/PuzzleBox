package com.ivandsky.puzzlebox.games.tangoui

import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/**
 * The module's golden harness (review I6). `tango.golden.mode` is `record` or `verify`:
 * record rewrites [golden] from [scratch]; verify fails on a missing golden, a size change
 * or, unless `-Pgoldens.pixelCompare=false`, any differing pixel. The golden classes do not
 * run at all without one of the two flags (see AndroidRoborazziConventionPlugin).
 */
internal object GoldenCompare {

    private val mode = System.getProperty("tango.golden.mode") ?: "verify"
    private val pixelCompare = System.getProperty("goldens.pixelCompare") != "false"

    fun check(golden: File, scratch: File, name: String) {
        when (mode) {
            "record" -> {
                golden.parentFile?.mkdirs()
                scratch.copyTo(golden, overwrite = true)
            }
            "verify" -> {
                if (!golden.exists()) {
                    throw AssertionError(
                        "Golden $name is missing and verify mode forbids recording; " +
                            "run with -Proborazzi.record=true once to record it, then re-verify.",
                    )
                }
                assertMatches(golden, scratch, name)
            }
        }
    }

    /** Pixel-exact on decoded colours, so PNG metadata noise cannot fail a run. */
    private fun assertMatches(golden: File, scratch: File, name: String) {
        val expected = ImageIO.read(golden)
        val actual = ImageIO.read(scratch)
        assertEquals("Golden $name drifted in width", expected.width, actual.width)
        assertEquals("Golden $name drifted in height", expected.height, actual.height)
        if (!pixelCompare) return
        val differing = (0 until expected.height).sumOf { y ->
            (0 until expected.width).count { x -> expected.getRGB(x, y) != actual.getRGB(x, y) }
        }
        assertTrue(
            "Golden $name drifted: $differing differing pixels. If intended, re-record " +
                "with -Proborazzi.record=true.",
            differing == 0,
        )
    }
}
