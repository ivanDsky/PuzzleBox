package com.ivandsky.puzzlebox.insights.stats.grouped

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** The stats page moves on the shared motion tokens only: nothing under `insights/stats` reaches for the recap's own motion. */
class StatsNoRecapMotionTest {

    private fun statsSources(): List<File> {
        val root = File(checkNotNull(System.getProperty("user.dir"))).resolve("src/main/kotlin/com/ivandsky/puzzlebox/insights/stats")
        return root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    @Test
    fun `no stats file mentions the recap motion`() {
        val files = statsSources()
        assertTrue("the stats sources are found", files.any { it.name == "StatsGroupedList.kt" })
        val offenders = files.filter { "RecapMotion" in it.readText() }.map { it.name }
        assertTrue("RecapMotion used by $offenders", offenders.isEmpty())
    }

    @Test
    fun `no stats motion uses a spring`() {
        val offenders = statsSources().filter { Regex("""\bspring\(|\bSpring\b|spring\(""").containsMatchIn(it.readText()) }.map { it.name }
        assertTrue("springs in $offenders", offenders.isEmpty())
    }
}
