package com.ivandsky.puzzlebox.tools.puzzlecli

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.trainer.GeneratedDrillSet
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.game.trainer.TechniqueIndex
import com.ivandsky.puzzlebox.model.GameId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** `generate-drills` at the [GenerateDrillsCommand] seam: a made-up game, an in-memory disk, no real engine. */
class GenerateDrillsCommandTest {

    private val a = DrillGameFixtures.A
    private val b = DrillGameFixtures.B
    private val c = DrillGameFixtures.C
    private val disk = MemoryFiles()
    private val outLines = mutableListOf<String>()
    private val errLines = mutableListOf<String>()
    private val indexPath = "idx/fakegame-index.txt"
    private val generatedPath = "out/fakegame-generated.txt"

    private fun indexText(techniqueSet: Int = 3, game: String = "fakegame", archived: Map<TechniqueId, Int> = emptyMap()): String =
        TechniqueIndex.write(
            TechniqueIndex(
                GameId(game), techniqueSet, LocalDate.of(2026, 11, 18),
                (1..9).associateWith { number -> archived.filterValues { it >= number }.mapValues { 1 } },
            ),
        )

    private fun run(vararg args: String, game: DrillGame = DrillGameFixtures.game(), files: MemoryFiles = disk): Int {
        outLines.clear()
        errLines.clear()
        return GenerateDrillsCommand(listOf(game), files.io, { outLines += it }, { errLines += it })
            .run(listOf("--index", "idx", "--out", "out") + args)
    }

    private fun generated(files: MemoryFiles = disk): GeneratedDrillSet =
        (GeneratedDrillSet.read(files.files.getValue(generatedPath)) as DecodeResult.Ok).value

    @Test
    fun `a technique the archive has too few puzzles of is topped up to five`() {
        disk.files[indexPath] = indexText(archived = mapOf(a to 2, b to 9, c to 9))
        assertEquals(CliExit.PUBLISHABLE, run())
        val drills = generated().drills
        assertEquals(5, drills.size)
        assertTrue(drills.all { it.technique == a })
        for (drill in drills) {
            assertEquals(drill.puzzle.payload[0].toInt(), drill.targetSteps)
            assertEquals("an a drill may not show b", 0, drill.puzzle.payload[1].toInt())
        }
        assertEquals(
            listOf(
                "fakegame a index=2 generated=5/5",
                "fakegame b index=9 generated=0/0",
                "fakegame c index=9 generated=0/0",
                "fakegame: 5 drills -> $generatedPath",
            ),
            outLines,
        )
    }

    @Test
    fun `a technique with the archive's five puzzles is not short`() {
        disk.files[indexPath] = indexText(archived = mapOf(a to 5, b to 5, c to 5))
        assertEquals(CliExit.PUBLISHABLE, run())
        assertFalse(generatedPath in disk.files)
        assertEquals(
            listOf("fakegame a index=5 generated=0/0", "fakegame b index=5 generated=0/0", "fakegame c index=5 generated=0/0"),
            outLines,
        )
    }

    @Test
    fun `per-technique moves both the bar and the quota`() {
        disk.files[indexPath] = indexText(archived = mapOf(a to 2, b to 1, c to 9))
        assertEquals(CliExit.PUBLISHABLE, run("--per-technique", "2"))
        assertEquals(listOf(b), generated().drills.map { it.technique }.distinct())
        assertEquals(2, generated().drills.size)
        assertEquals("fakegame b index=1 generated=2/2", outLines[1])
    }

    @Test
    fun `several short techniques share one file and no puzzle serves two`() {
        disk.files[indexPath] = indexText(archived = mapOf(c to 9))
        assertEquals(CliExit.PUBLISHABLE, run())
        val drills = generated().drills
        assertEquals(10, drills.size)
        assertEquals(5, drills.count { it.technique == a })
        assertEquals(5, drills.count { it.technique == b })
        assertEquals(10, drills.map { it.id }.toSet().size)
    }

    @Test
    fun `a technique the generator cannot prove is a shortfall, exit 1, and the rest is still written`() {
        disk.files[indexPath] = indexText(archived = mapOf(a to 1, b to 9, c to 0))
        assertEquals(CliExit.NOT_PUBLISHABLE, run("--max-attempts", "50"))
        assertEquals(listOf(a), generated().drills.map { it.technique }.distinct())
        assertTrue("fakegame c index=0 generated=0/5" in outLines)
        assertEquals(listOf("fakegame c: 0 of 5 within 50 attempts"), errLines)
    }

    @Test
    fun `the written file reads back as the set that was written`() {
        disk.files[indexPath] = indexText(archived = mapOf(b to 9, c to 9))
        run()
        val set = generated()
        assertEquals(GameId("fakegame"), set.gameId)
        assertEquals(3, set.techniqueSetVersion)
        assertEquals(disk.files.getValue(generatedPath), GeneratedDrillSet.write(set))
    }

    @Test
    fun `a file that does not read back stops the run`() {
        disk.files[indexPath] = indexText(archived = mapOf(b to 9, c to 9))
        val lossy = MemoryFiles(corrupt = { path, text -> if (path == generatedPath) text.dropLast(5) else text })
        lossy.files[indexPath] = disk.files.getValue(indexPath)
        try {
            run(files = lossy)
            throw AssertionError("expected the read-back check to stop the run")
        } catch (stopped: IllegalStateException) {
            assertTrue(stopped.message.orEmpty().contains(generatedPath))
        }
    }

    @Test
    fun `the same seed writes the same file and another seed does not`() {
        disk.files[indexPath] = indexText(archived = mapOf(c to 9))
        run("--seed", "5")
        val first = disk.files.getValue(generatedPath)
        run("--seed", "5")
        assertEquals(first, disk.files.getValue(generatedPath))
        run("--seed", "6")
        assertNotEquals(first, disk.files.getValue(generatedPath))
    }

    @Test
    fun `a game with no index file is skipped, never counted as having no puzzles`() {
        disk.files[indexPath] = indexText(archived = mapOf(a to 9, b to 9, c to 9))
        val other = DrillGameFixtures.other()
        val code = GenerateDrillsCommand(listOf(other, DrillGameFixtures.game()), disk.io, { outLines += it }, { errLines += it })
            .run(listOf("--index", "idx", "--out", "out"))
        assertEquals(CliExit.PUBLISHABLE, code)
        assertEquals(1, errLines.size)
        assertTrue(errLines.single().startsWith("othergame: no technique index at idx"))
        assertTrue(outLines.none { it.startsWith("othergame") })
        assertEquals(setOf(indexPath), disk.files.keys)
    }

    @Test
    fun `no index file at all is a usage error`() {
        assertEquals(CliExit.USAGE, run())
        assertTrue(errLines.any { it.startsWith("no technique index found") })
        assertEquals(GenerateDrillsCommand.USAGE, errLines.last())
        assertTrue(outLines.isEmpty())
    }

    @Test
    fun `a malformed index is exit 2 and writes nothing for that game`() {
        disk.files[indexPath] = "technique-index 1\ngame fakegame\n"
        assertEquals(CliExit.INVALID_TEXT, run())
        assertTrue(errLines.single().startsWith("fakegame: cannot read $indexPath"))
        assertFalse(generatedPath in disk.files)
    }

    @Test
    fun `another game's index under this game's name is exit 2`() {
        disk.files[indexPath] = indexText(game = "othergame")
        assertEquals(CliExit.INVALID_TEXT, run())
        assertTrue(errLines.single().contains("technique index of othergame"))
    }

    @Test
    fun `a bad index does not stop the other games`() {
        disk.files[indexPath] = "nonsense"
        val other = DrillGameFixtures.other()
        disk.files["idx/othergame-index.txt"] = indexText(game = "othergame")
        val code = GenerateDrillsCommand(listOf(DrillGameFixtures.game(), other), disk.io, { outLines += it }, { errLines += it })
            .run(listOf("--index", "idx", "--out", "out"))
        assertEquals(CliExit.INVALID_TEXT, code)
        assertTrue("out/othergame-generated.txt" in disk.files)
    }

    @Test
    fun `an index walked with another technique set only warns`() {
        disk.files[indexPath] = indexText(techniqueSet = 2, archived = mapOf(a to 9, b to 9, c to 9))
        assertEquals(CliExit.PUBLISHABLE, run())
        assertEquals(1, errLines.size)
        assertTrue(errLines.single().contains("technique set 2"))
    }

    @Test
    fun `bad arguments print the reason and the usage, exit 64, and touch nothing`() {
        disk.files[indexPath] = indexText()
        val code = GenerateDrillsCommand(listOf(DrillGameFixtures.game()), disk.io, { outLines += it }, { errLines += it })
            .run(listOf("--index", "idx"))
        assertEquals(CliExit.USAGE, code)
        assertEquals(2, errLines.size)
        assertTrue(errLines[0].contains("--out"))
        assertEquals(GenerateDrillsCommand.USAGE, errLines[1])
        assertEquals(setOf(indexPath), disk.files.keys)
    }
}
