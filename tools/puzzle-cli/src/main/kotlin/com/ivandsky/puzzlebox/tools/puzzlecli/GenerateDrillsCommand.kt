package com.ivandsky.puzzlebox.tools.puzzlecli

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.trainer.GeneratedDrillSet
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.game.trainer.TechniqueIndex
import java.io.File
import kotlin.random.Random

/**
 * `generate-drills --index <dir> --out <dir> [--seed <long>] [--per-technique <n>] [--max-attempts <n>]` (spec §2.5):
 * for each game with a technique walker, reads `<index>/<game>-index.txt` (the file `index` writes), finds the catalog
 * techniques that fewer than `--per-technique` archive puzzles carry, generates puzzles for each of them and keeps one
 * only when [DrillKeep] says the walk proves the technique (Mini Sudoku: and nothing from a harder family). A game with
 * at least one short technique gets `<out>/<game>-generated.txt`, a [GeneratedDrillSet] the game's drill source reads.
 * Offline, deterministic for a seed, and by hand on a dev machine: the device never generates (A7).
 *
 * A game with no index file is skipped, never counted as having no puzzles at all. Each technique gets its own random
 * stream (seed, game, technique), so a technique's drills do not move when another technique's search changes. A
 * technique is drilled by at most `--per-technique` puzzles, none of them serving a second technique in the same file.
 * One summary line per catalog technique goes to [out]: `<game> <technique> index=<n> generated=<k>/<want>`, where
 * `want` is 0 for a technique the archive covers. Exit 0 when every short technique reached its `want`.
 *
 * @param games the games to cover, each with its walker and generator (see [DrillGames.of])
 */
class GenerateDrillsCommand(
    private val games: List<DrillGame>,
    private val io: IndexIo,
    private val out: (String) -> Unit,
    private val err: (String) -> Unit,
) {

    /** Runs the command for [args] (everything after `generate-drills`); returns a [CliExit] code. */
    fun run(args: List<String>): Int {
        val options = try {
            GenerateDrillsOptions.parse(args)
        } catch (bad: IllegalArgumentException) {
            err(bad.message ?: "bad arguments")
            err(USAGE)
            return CliExit.USAGE
        }
        val root = Random(options.seed).nextLong()
        var indexed = 0
        var broken = 0
        val shortfalls = ArrayList<String>()
        for (game in games) {
            val index = when (val read = readIndex(game, options)) {
                is IndexRead.Missing -> continue
                is IndexRead.Broken -> {
                    broken++
                    continue
                }
                is IndexRead.Found -> read.index
            }
            indexed++
            shortfalls += cover(game, index, options, root)
        }
        shortfalls.forEach(err)
        return when {
            broken > 0 -> CliExit.INVALID_TEXT
            indexed == 0 -> {
                err("no technique index found under ${options.index}")
                err(USAGE)
                CliExit.USAGE
            }
            shortfalls.isNotEmpty() -> CliExit.NOT_PUBLISHABLE
            else -> CliExit.PUBLISHABLE
        }
    }

    private sealed interface IndexRead {
        object Missing : IndexRead
        object Broken : IndexRead
        class Found(val index: TechniqueIndex) : IndexRead
    }

    private fun readIndex(game: DrillGame, options: GenerateDrillsOptions): IndexRead {
        val path = File(options.index, "${game.gameId}-index.txt").path
        val text = io.readText(path)
        if (text == null) {
            err("${game.gameId}: no technique index at $path, skipped")
            return IndexRead.Missing
        }
        val index = when (val read = TechniqueIndex.read(text)) {
            is DecodeResult.Ok -> read.value
            is DecodeResult.Err -> {
                err("${game.gameId}: cannot read $path: ${read.error}")
                return IndexRead.Broken
            }
        }
        if (index.gameId != game.gameId) {
            err("${game.gameId}: $path is the technique index of ${index.gameId}")
            return IndexRead.Broken
        }
        if (index.techniqueSetVersion != game.walker.techniqueSetVersion) {
            err(
                "${game.gameId}: $path was walked with technique set ${index.techniqueSetVersion}, " +
                    "the walker is at ${game.walker.techniqueSetVersion}; its counts may be stale",
            )
        }
        return IndexRead.Found(index)
    }

    /** Generates drills for [game]'s short techniques, prints its summary lines, writes its set; returns its shortfalls. */
    private fun cover(game: DrillGame, index: TechniqueIndex, options: GenerateDrillsOptions, root: Long): List<String> {
        val drills = ArrayList<GeneratedDrillSet.Drill>()
        val shortfalls = ArrayList<String>()
        var short = 0
        for (technique in game.techniques) {
            val archived = index.entries.values.count { technique in it }
            if (archived >= options.perTechnique) {
                out("${game.gameId} $technique index=$archived generated=0/0")
                continue
            }
            short++
            val random = Random(root xor "${game.gameId}/$technique".hashCode().toLong())
            val found = DrillSearch.find(
                game, technique, options.perTechnique, options.maxAttempts, random, taken = drills.mapTo(HashSet()) { it.id },
            )
            drills += found
            out("${game.gameId} $technique index=$archived generated=${found.size}/${options.perTechnique}")
            if (found.size < options.perTechnique) {
                shortfalls += "${game.gameId} $technique: ${found.size} of ${options.perTechnique} within ${options.maxAttempts} attempts"
            }
        }
        if (short > 0) write(game, drills, options)
        return shortfalls
    }

    private fun write(game: DrillGame, drills: List<GeneratedDrillSet.Drill>, options: GenerateDrillsOptions) {
        val set = GeneratedDrillSet(game.gameId, game.walker.techniqueSetVersion, drills)
        val text = GeneratedDrillSet.write(set)
        val path = File(options.out, "${game.gameId}-generated.txt").path
        io.writeText(path, text)
        val readBack = io.readText(path)?.let { GeneratedDrillSet.read(it) }
        check(readBack is DecodeResult.Ok && GeneratedDrillSet.write(readBack.value) == text) {
            "$path does not read back as the set that was written: $readBack"
        }
        out("${game.gameId}: ${drills.size} drills -> $path")
    }

    companion object {
        /** The usage line of `generate-drills`. */
        const val USAGE: String =
            "puzzle-cli generate-drills --index <dir> --out <dir> [--seed <long>] [--per-technique <1-5>] [--max-attempts <n>]"
    }
}
