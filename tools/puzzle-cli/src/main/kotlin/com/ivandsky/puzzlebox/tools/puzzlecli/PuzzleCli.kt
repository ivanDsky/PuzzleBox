package com.ivandsky.puzzlebox.tools.puzzlecli

import com.ivandsky.puzzlebox.game.DailySource
import com.ivandsky.puzzlebox.game.FeedTransport
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.NotationText
import com.ivandsky.puzzlebox.games.queens.feed.LinkedInQueensDailySource
import com.ivandsky.puzzlebox.games.queens.queensPlugin
import com.ivandsky.puzzlebox.games.strands.strandsPlugin
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudoku.feed.LinkedInMiniSudokuDailySource
import com.ivandsky.puzzlebox.games.sudoku.sudokuPlugin
import com.ivandsky.puzzlebox.games.tango.feed.LinkedInTangoDailySource
import com.ivandsky.puzzlebox.games.tango.tangoPlugin
import com.ivandsky.puzzlebox.games.zip.feed.LinkedInZipDailySource
import com.ivandsky.puzzlebox.games.zip.zipPlugin
import com.ivandsky.puzzlebox.network.LINKEDIN_BASE_URL
import com.ivandsky.puzzlebox.network.linkedInFeedTransport
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Clock
import kotlin.time.Duration
import kotlin.system.exitProcess

/**
 * The composition root: the one file that builds the plugin registry. [DrillGames] and the drill generators beside it
 * also name games, because `generate-drills` needs each game's own puzzle builder (ADR-0021 §4).
 */
object PuzzleCli {

    /** Every non-UI game plugin; a game reaches the CLI only via NotationText/GamePlugin.editor. */
    val registry: GameRegistry = GameRegistry(
        listOf(
            zipPlugin,
            queensPlugin,
            tangoPlugin,
            sudokuPlugin(SudokuGames.MINI),
            sudokuPlugin(SudokuGames.CLASSIC),
            strandsPlugin,
        ),
    )

    /** Analysis budget per run: past it the verdict is "undetermined". */
    const val BUDGET_MS: Long = 30_000

    private const val USAGE_MESSAGE =
        "usage: puzzle-cli validate <file> | puzzle-cli solve <file> | puzzle-cli share <file> | ${IndexCommand.USAGE} | ${GenerateDrillsCommand.USAGE}"

    private val json = Json

    /**
     * `validate <file>` (since M5-03) | `solve <file>` (since M5-04). Prints one CliVerdict
     * JSON line to [out]; returns a CliExit code.
     */
    fun run(args: List<String>, read: (path: String) -> String, out: (String) -> Unit, err: (String) -> Unit): Int {
        val command = args.getOrNull(0)
        val path = args.getOrNull(1)
        val withSolution = command == "solve"
        if ((command != "validate" && command != "solve") || path == null || args.size != 2) {
            err(USAGE_MESSAGE)
            return CliExit.USAGE
        }
        val text = try {
            read(path)
        } catch (failure: Exception) {
            err("cannot read $path: ${failure.message}")
            return CliExit.USAGE
        }
        val parse = NotationText.parse(text, registry)
        val verdict = CliVerdicts.of(parse, Budget.within(BUDGET_MS, System::nanoTime), withSolution = withSolution)
        out(json.encodeToString(CliVerdict.serializer(), verdict))
        return exitCode(verdict)
    }

    /**
     * The LinkedIn daily sources of every game the index pass may cover, on one shared [transport]
     * (one session, one throttle cooldown). Which of them it walks is [indexTargets]' call.
     */
    private fun linkedInSources(transport: FeedTransport): List<DailySource> = listOf(
        LinkedInQueensDailySource(transport),
        LinkedInTangoDailySource(transport),
        LinkedInMiniSudokuDailySource(transport),
        LinkedInZipDailySource(transport),
    )

    /** The games the index pass walks over [transport]: each LinkedIn source whose plugin has a technique walker. */
    fun indexTargets(transport: FeedTransport): List<IndexTarget> =
        IndexTargets.pair(linkedInSources(transport), registry)

    /**
     * `index` (see [IndexCommand]) over [transport], the clock and the file seams; returns a CliExit
     * code. [args] are the arguments after `index`.
     */
    suspend fun runIndex(
        args: List<String>,
        transport: FeedTransport,
        clock: Clock,
        sleep: suspend (Duration) -> Unit,
        io: IndexIo,
        out: (String) -> Unit,
        err: (String) -> Unit,
    ): Int = IndexCommand(indexTargets(transport), clock, sleep, io, out, err, progress = out).run(args)

    /**
     * `generate-drills` (see [GenerateDrillsCommand]) over every registered game with a technique walker, on the
     * file seams [io]; returns a CliExit code. [args] are the arguments after `generate-drills`.
     */
    fun runGenerateDrills(args: List<String>, io: IndexIo, out: (String) -> Unit, err: (String) -> Unit): Int =
        GenerateDrillsCommand(DrillGames.of(registry), io, out, err).run(args)

    /** `share` (see [ShareCommand]); returns a CliExit code. [args] are the arguments after `share`. */
    fun runShare(args: List<String>, read: (path: String) -> String, out: (String) -> Unit, err: (String) -> Unit): Int =
        ShareCommand(read, out, err).run(args)

    /** 0 ⇔ unique with no board-level publish block (§9.2); malformed text is 2. */
    private fun exitCode(verdict: CliVerdict): Int =
        when {
            verdict.verdict == "malformed" -> CliExit.INVALID_TEXT
            verdict.verdict == "unique" && verdict.blocks.isEmpty() -> CliExit.PUBLISHABLE
            else -> CliExit.NOT_PUBLISHABLE
        }
}

fun main(args: Array<String>) {
    if (args.firstOrNull() == "index") {
        // The real LinkedIn transport, exactly as networkModule builds it: no cookies, no credentials (ADR-0021 §1).
        val transport = linkedInFeedTransport(LINKEDIN_BASE_URL, Json { ignoreUnknownKeys = true })
        val code = runBlocking {
            PuzzleCli.runIndex(
                args.drop(1), transport, Clock.systemUTC(), { delay(it) }, IndexIo.files(), ::println, System.err::println,
            )
        }
        exitProcess(code)
    }
    if (args.firstOrNull() == "generate-drills") {
        // Offline: no transport, no network (A7); the walkers and generators run on this machine.
        exitProcess(PuzzleCli.runGenerateDrills(args.drop(1), IndexIo.files(), ::println, System.err::println))
    }
    if (args.firstOrNull() == "share") {
        exitProcess(PuzzleCli.runShare(args.drop(1), { path -> File(path).readText() }, ::println, System.err::println))
    }
    exitProcess(PuzzleCli.run(args.toList(), { path -> File(path).readText() }, ::println, System.err::println))
}
