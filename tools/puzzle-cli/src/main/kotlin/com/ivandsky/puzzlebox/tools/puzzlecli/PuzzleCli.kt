package com.ivandsky.puzzlebox.tools.puzzlecli

import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.NotationText
import com.ivandsky.puzzlebox.games.queens.queensPlugin
import com.ivandsky.puzzlebox.games.strands.strandsPlugin
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudoku.sudokuPlugin
import com.ivandsky.puzzlebox.games.tango.tangoPlugin
import com.ivandsky.puzzlebox.games.zip.zipPlugin
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.system.exitProcess

/** The composition root and the only file under tools/ that names games (ADR-0021 §4). */
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

    private const val USAGE_MESSAGE = "usage: puzzle-cli validate <file>"

    private val json = Json

    /**
     * `validate <file>` (since M5-03) | `solve <file>` (since M5-04). Prints one CliVerdict
     * JSON line to [out]; returns a CliExit code.
     */
    fun run(args: List<String>, read: (path: String) -> String, out: (String) -> Unit, err: (String) -> Unit): Int {
        val command = args.getOrNull(0)
        val path = args.getOrNull(1)
        if (command != "validate" || path == null || args.size != 2) {
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
        val verdict = CliVerdicts.of(parse, Budget.within(BUDGET_MS, System::nanoTime), withSolution = false)
        out(json.encodeToString(CliVerdict.serializer(), verdict))
        return exitCode(verdict)
    }

    /** 0 ⇔ unique with no board-level publish block (§9.2); malformed text is 2. */
    private fun exitCode(verdict: CliVerdict): Int =
        when {
            verdict.verdict == "malformed" -> CliExit.INVALID_TEXT
            verdict.verdict == "unique" && verdict.blocks.isEmpty() -> CliExit.PUBLISHABLE
            else -> CliExit.NOT_PUBLISHABLE
        }
}

fun main(args: Array<String>) {
    exitProcess(PuzzleCli.run(args.toList(), { path -> File(path).readText() }, ::println, System.err::println))
}
