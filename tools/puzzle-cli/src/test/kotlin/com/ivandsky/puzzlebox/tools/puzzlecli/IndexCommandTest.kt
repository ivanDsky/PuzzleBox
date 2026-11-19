package com.ivandsky.puzzlebox.tools.puzzlecli

import com.ivandsky.puzzlebox.game.DailyFetch
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.FetchResult
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.game.trainer.TechniqueIndex
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.time.Duration.Companion.seconds

/**
 * The index pass at the [IndexCommand] seam: fake daily sources, a fake walker, an in-memory disk and a
 * recording sleep, so no test waits, fetches over the network or touches a file.
 */
class IndexCommandTest {

    private val game = GameId("fakegame")
    private val since = LocalDate.of(2026, 11, 1)

    /** 2026-11-06 12:00 UTC: yesterday is 2026-11-05, so the range is #1..#5. */
    private val clock = Clock.fixed(Instant.parse("2026-11-06T12:00:00Z"), ZoneOffset.UTC)
    private val events = mutableListOf<String>()
    private val disk = MemoryFiles()
    private val outLines = mutableListOf<String>()
    private val errLines = mutableListOf<String>()
    private val checkpointPath = "cp.txt"
    private val indexPath = "idx/fakegame-index.txt"

    private fun day(n: Int): LocalDate = since.plusDays(n - 1L)

    private fun source(
        reply: (LocalDate, Int) -> FetchResult<DailyFetch> =
            { d, _ -> ScriptedDailySource.puzzleOf(game, d, since) },
    ) = ScriptedDailySource(game, since, events, reply)

    private suspend fun run(
        source: ScriptedDailySource,
        vararg args: String,
        files: MemoryFiles = disk,
    ): Int {
        val command = IndexCommand(
            targets = listOf(IndexTarget(source, FakeTechniqueWalker(), ZoneOffset.UTC)),
            clock = clock,
            sleep = { events += "sleep ${it.inWholeSeconds}s" },
            io = files.io,
            out = { outLines += it },
            err = { errLines += it },
        )
        val flags = args.toList().ifEmpty { listOf("--out", "idx", "--checkpoint", checkpointPath) }
        return command.run(flags)
    }

    private fun readIndex(): TechniqueIndex = (TechniqueIndex.read(disk.files.getValue(indexPath)) as DecodeResult.Ok).value

    @Test
    fun `every fetch is followed by the ten second pace`() = runTest {
        val source = source()
        assertEquals(CliExit.PUBLISHABLE, run(source))
        assertEquals(10.seconds, IndexCommand.PACE)
        assertEquals((1..5).flatMap { listOf("fetch ${day(it)}", "sleep 10s") }, events)
    }

    @Test
    fun `a throttle sleeps the cooldown and retries the same number`() = runTest {
        val throttled = FetchResult.Err(FetchError.Throttled("cooldown"))
        val source = source { d, attempt ->
            if (d == day(2) && attempt < 2) throttled else ScriptedDailySource.puzzleOf(game, d, since)
        }
        assertEquals(CliExit.PUBLISHABLE, run(source))
        assertEquals(listOf(1, 2, 2, 2, 3, 4, 5).map { day(it) }, source.fetched)
        val twoAt = events.indexOf("fetch ${day(2)}")
        assertEquals(
            listOf("fetch ${day(2)}", "sleep 10s", "sleep 100s", "fetch ${day(2)}", "sleep 10s", "sleep 100s", "fetch ${day(2)}", "sleep 10s"),
            events.subList(twoAt, twoAt + 8),
        )
    }

    @Test
    fun `three failures in a row stop the run with everything before them kept and no index`() = runTest {
        val source = source { d, _ ->
            if (d == day(3)) FetchResult.Err(FetchError.Unavailable("edge")) else ScriptedDailySource.puzzleOf(game, d, since)
        }
        assertEquals(CliExit.STOPPED, run(source))
        assertEquals(listOf(1, 2, 3, 3, 3).map { day(it) }, source.fetched)
        assertEquals(2, events.count { it == "sleep 100s" })
        assertEquals("sleep 10s", events.last())
        assertEquals(
            listOf("puzzle-cli-index-checkpoint 1", "fakegame 1 ok singles=1", "fakegame 2 ok singles=2"),
            disk.files.getValue(checkpointPath).lines().filter { it.isNotEmpty() },
        )
        assertFalse(indexPath in disk.files)
        assertTrue(errLines.any { "Rerun the same command to resume" in it && "#3" in it })
    }

    @Test
    fun `a settled number between failures resets the count`() = runTest {
        val source = source { d, attempt ->
            if (d != day(5) && attempt < 2) FetchResult.Err(FetchError.Throttled()) else ScriptedDailySource.puzzleOf(game, d, since)
        }
        assertEquals(CliExit.PUBLISHABLE, run(source))
        assertEquals(8, events.count { it == "sleep 100s" })
        assertTrue(indexPath in disk.files)
    }

    @Test
    fun `a rerun over the checkpoint fetches only the open numbers and writes the index`() = runTest {
        var blocked = true
        val first = source { d, _ ->
            if (blocked && d == day(3)) FetchResult.Err(FetchError.Throttled()) else ScriptedDailySource.puzzleOf(game, d, since)
        }
        assertEquals(CliExit.STOPPED, run(first))
        blocked = false
        val second = source()
        assertEquals(CliExit.PUBLISHABLE, run(second))
        assertEquals(listOf(3, 4, 5).map { day(it) }, second.fetched)
        assertEquals((1..5).toSet(), readIndex().entries.keys)
    }

    @Test
    fun `a torn last line is dropped and its number fetched again`() = runTest {
        assertEquals(CliExit.PUBLISHABLE, run(source()))
        disk.files.remove(indexPath)
        val whole = disk.files.getValue(checkpointPath)
        disk.files[checkpointPath] = whole.removeSuffix("fakegame 5 ok singles=5\n") + "fakegame 5 ok singl"
        val again = source()
        assertEquals(CliExit.PUBLISHABLE, run(again))
        assertEquals(listOf(day(5)), again.fetched)
        assertTrue(errLines.any { "torn" in it })
        assertEquals(whole, disk.files.getValue(checkpointPath))
    }

    @Test
    fun `a last line cut after a parsable prefix is dropped too`() = runTest {
        assertEquals(CliExit.PUBLISHABLE, run(source()))
        disk.files[checkpointPath] = disk.files.getValue(checkpointPath).removeSuffix("\n")
        val again = source()
        assertEquals(CliExit.PUBLISHABLE, run(again))
        assertEquals(listOf(day(5)), again.fetched)
    }

    @Test
    fun `a malformed middle line or another header version is usage and fetches nothing`() = runTest {
        val bad = listOf(
            "puzzle-cli-index-checkpoint 1\nfakegame 1 ok singles=1\nnonsense\nfakegame 3 ok singles=3\n",
            "puzzle-cli-index-checkpoint 2\n",
            "something else\n",
        )
        for (text in bad) {
            val source = source()
            disk.files[checkpointPath] = text
            assertEquals(text, CliExit.USAGE, run(source))
            assertTrue(source.fetched.isEmpty())
        }
    }

    @Test
    fun `not found untrusted and unwalkable numbers are skipped with their kind and left out of the index`() = runTest {
        val source = source { d, _ ->
            when (d) {
                day(2) -> FetchResult.Err(FetchError.NotFound("no\nsuch puzzle"))
                day(3) -> FetchResult.Err(FetchError.Untrusted("gate says no"))
                day(4) -> ScriptedDailySource.puzzleOf(game, d, since, walkable = false)
                else -> ScriptedDailySource.puzzleOf(game, d, since)
            }
        }
        assertEquals(CliExit.PUBLISHABLE, run(source))
        assertEquals(5, source.fetched.size)
        val checkpoint = disk.files.getValue(checkpointPath)
        assertTrue(checkpoint, "fakegame 2 skip NotFound no such puzzle\n" in checkpoint)
        assertTrue(checkpoint, "fakegame 3 skip Untrusted gate says no\n" in checkpoint)
        assertTrue(checkpoint, "fakegame 4 skip Unwalkable " in checkpoint)
        assertEquals(setOf(1, 5), readIndex().entries.keys)
        val summary = outLines.single()
        assertTrue(summary, "ok 2, skipped 3 (NotFound 1, Untrusted 1, Unwalkable 1)" in summary)
    }

    @Test
    fun `the index reads back to the game version cutoff and entries`() = runTest {
        assertEquals(CliExit.PUBLISHABLE, run(source()))
        val expected = TechniqueIndex(
            gameId = game,
            techniqueSetVersion = 3,
            cutoff = day(5),
            entries = (1..5).associateWith { mapOf(TechniqueId("singles") to it) },
        )
        assertEquals(expected, readIndex())
        assertTrue(outLines.single(), "#1..#5" in outLines.single() && indexPath in outLines.single())
    }

    @Test
    fun `until clamps the range and the cutoff`() = runTest {
        val source = source()
        assertEquals(CliExit.PUBLISHABLE, run(source, "--out", "idx", "--checkpoint", checkpointPath, "--until", day(3).toString()))
        assertEquals(listOf(1, 2, 3).map { day(it) }, source.fetched)
        assertEquals(day(3), readIndex().cutoff)
    }

    @Test
    fun `an index that does not read back fails loudly`() = runTest {
        val files = MemoryFiles { path, text -> if (path == indexPath) text.replace("singles=1", "singles=9") else text }
        try {
            run(source(), files = files)
            fail("expected the read-back check to fail")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message, "does not read back" in expected.message.orEmpty())
        }
    }

    @Test
    fun `bad arguments are usage and nothing is fetched`() = runTest {
        val cp = checkpointPath
        val bad = listOf(
            listOf("--checkpoint", cp),
            listOf("--out", "idx"),
            listOf("--out", "idx", "--checkpoint", cp, "--until", "yesterday"),
            listOf("--out", "idx", "--checkpoint", cp, "--until", day(5).plusDays(1).toString()),
            listOf("--out", "idx", "--checkpoint", cp, "--bogus", "x"),
            listOf("--out", "idx", "--checkpoint"),
            listOf("--out", "idx", "--out", "idx2", "--checkpoint", cp),
        )
        for (args in bad) {
            val source = source()
            assertEquals(args.toString(), CliExit.USAGE, run(source, *args.toTypedArray()))
            assertTrue(source.fetched.isEmpty())
        }
        assertTrue(errLines.any { IndexCommand.USAGE in it })
        assertTrue(disk.files.isEmpty())
    }
}
