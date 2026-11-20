package com.ivandsky.puzzlebox.tools.puzzlecli

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.FetchResult
import com.ivandsky.puzzlebox.game.trainer.TechniqueIndex
import com.ivandsky.puzzlebox.network.LINKEDIN_THROTTLE_COOLDOWN
import java.io.File
import java.time.Clock
import java.time.LocalDate
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toKotlinDuration

/**
 * `index --out <dir> --checkpoint <file> [--until <yyyy-mm-dd>] [--pace <seconds>]` (ADR-0017 §5, §6 amendments):
 * walks every archive daily of each game whose plugin has a technique walker and writes that game's
 * technique index. The files go to `games/<game>-ui/src/main/resources/trainer/<game>-index.txt`,
 * which the drill sources read. It runs by hand, off the player's network, and never in CI: the
 * shipped index files are committed resources and CI never regenerates them.
 *
 * Per game it covers every day from the source's `archiveSince` to the game calendar's yesterday
 * (`fetchPast` serves only days before today), or to `--until` when given. It fetches one number at
 * a time, games one after another from one IP, and sleeps [PACE] after every fetch: the app's own
 * budget of 6 calls a minute (`LinkedInCallBudget` in core:data). A throttled or unavailable fetch
 * sleeps [LINKEDIN_THROTTLE_COOLDOWN] and retries the same number; [MAX_FAILURES_IN_A_ROW] such
 * failures in a row stop the run with [CliExit.STOPPED] and write no index file. Each settled
 * number is appended to the [IndexCheckpoint] file as it settles, so a rerun over the same file
 * fetches only the numbers still open. Only counts are kept: no puzzle content is written anywhere.
 *
 * @param targets the games to walk, paired with their walkers (see [IndexTargets.pair])
 * @param clock the instant "today" is read from, in each game's own calendar zone
 * @param sleep waits for a [Duration]; production passes `delay`
 * @param progress one timestamped line per settled number (its checkpoint line); failures and skips
 *   also go to [err], so the error stream alone lists everything that went wrong
 */
class IndexCommand(
    private val targets: List<IndexTarget>,
    private val clock: Clock,
    private val sleep: suspend (Duration) -> Unit,
    private val io: IndexIo,
    private val out: (String) -> Unit,
    private val err: (String) -> Unit,
    private val progress: (String) -> Unit = {},
) {

    private class Walk(val target: IndexTarget, val days: List<LocalDate>)

    /** Runs the pass for [args] (everything after `index`); returns a [CliExit] code. */
    suspend fun run(args: List<String>): Int {
        val options = try {
            IndexOptions.parse(args).also { check(it) }
        } catch (bad: IllegalArgumentException) {
            return usage(bad.message ?: "bad arguments")
        }
        var checkpoint = when (val parsed = IndexCheckpoint.parse(io.readText(options.checkpoint).orEmpty())) {
            is DecodeResult.Ok -> parsed.value
            is DecodeResult.Err -> return usage("cannot resume from ${options.checkpoint}: ${parsed.error}")
        }
        checkpoint.tornLine?.let { err("dropped a torn last line from ${options.checkpoint}: '$it'") }
        if (io.readText(options.checkpoint) != checkpoint.text()) io.writeText(options.checkpoint, checkpoint.text())

        val walks = targets.map { Walk(it, days(it, options.until)) }
        val total = walks.sumOf { it.days.size }
        var seen = 0
        for (walk in walks) {
            val game = walk.target.source.gameId
            for (day in walk.days) {
                seen++
                val number = checkNotNull(walk.target.source.puzzleNumber(day)) { "$game has no number for $day" }
                if (checkpoint.settlement(game, number) != null) continue
                val settlement = settle(walk.target, day, number, options.pace)
                if (settlement == null) {
                    err("${clock.instant()} stopped: $MAX_FAILURES_IN_A_ROW fetches in a row failed at $game #$number ($day). Rerun the same command to resume.")
                    return CliExit.STOPPED
                }
                checkpoint = checkpoint.with(game, number, settlement)
                val line = IndexCheckpoint.line(game, number, settlement)
                io.appendLine(options.checkpoint, line)
                progress("${clock.instant()} [$seen/$total] $day $line")
                if (settlement is Settlement.Skip) err("${clock.instant()} $day $line")
            }
        }
        for (walk in walks) summarize(walk, checkpoint, options.out)
        return CliExit.PUBLISHABLE
    }

    private fun check(options: IndexOptions) {
        val until = options.until ?: return
        for (target in targets) {
            val yesterday = yesterday(target)
            require(!until.isAfter(yesterday)) { "--until $until is after ${target.source.gameId}'s yesterday $yesterday" }
        }
    }

    private fun usage(reason: String): Int {
        err(reason)
        err(USAGE)
        return CliExit.USAGE
    }

    private fun yesterday(target: IndexTarget): LocalDate = LocalDate.now(clock.withZone(target.zone)).minusDays(1)

    /** Every day of [target]'s archive through [until] or the game's yesterday; empty when the source has no archive. */
    private fun days(target: IndexTarget, until: LocalDate?): List<LocalDate> {
        val since = target.source.archiveSince ?: return emptyList()
        val last = until ?: yesterday(target)
        return generateSequence(since) { it.plusDays(1) }.takeWhile { !it.isAfter(last) }.toList()
    }

    /**
     * Fetches [number] until it settles; null when [MAX_FAILURES_IN_A_ROW] fetches in a row failed.
     * A failure retries the same number, so the run of failures is this number's retries and any
     * settled number starts the next run at zero.
     */
    private suspend fun settle(target: IndexTarget, day: LocalDate, number: Int, pace: Duration): Settlement? {
        var failed = 0
        while (true) {
            val fetched = target.source.fetchPast(day)
            sleep(pace)
            when (fetched) {
                is FetchResult.Ok -> {
                    val counts = target.walker.techniqueCounts(fetched.value.puzzle)
                    return if (counts.isEmpty()) {
                        Settlement.Skip(SkipKind.Unwalkable, "the walker could not walk #$number")
                    } else {
                        Settlement.Ok(counts)
                    }
                }
                is FetchResult.Err -> when (val error = fetched.error) {
                    is FetchError.NotFound -> return Settlement.Skip(SkipKind.NotFound, IndexCheckpoint.oneLine(error.detail.orEmpty()))
                    is FetchError.Untrusted -> return Settlement.Skip(SkipKind.Untrusted, IndexCheckpoint.oneLine(error.detail))
                    is FetchError.Throttled, is FetchError.Unavailable -> {
                        failed++
                        val detail = (error as? FetchError.Throttled)?.detail ?: (error as? FetchError.Unavailable)?.detail
                        val what = "${clock.instant()} $day ${target.source.gameId} $number ${error::class.simpleName} (${detail.orEmpty()})"
                        if (failed >= MAX_FAILURES_IN_A_ROW) {
                            err("$what, failure $failed of $MAX_FAILURES_IN_A_ROW")
                            return null
                        }
                        err("$what, failure $failed of $MAX_FAILURES_IN_A_ROW; retrying after ${LINKEDIN_THROTTLE_COOLDOWN.seconds} s")
                        sleep(LINKEDIN_THROTTLE_COOLDOWN.toKotlinDuration())
                    }
                }
            }
        }
    }

    private fun summarize(walk: Walk, checkpoint: IndexCheckpoint, dir: String) {
        val game = walk.target.source.gameId
        if (walk.days.isEmpty()) {
            out("$game: no days in range, nothing written")
            return
        }
        val numbers = walk.days.map { checkNotNull(walk.target.source.puzzleNumber(it)) }
        val settled = numbers.map { checkNotNull(checkpoint.settlement(game, it)) }
        val entries = numbers.zip(settled).mapNotNull { (number, s) -> (s as? Settlement.Ok)?.let { number to it.counts } }.toMap()
        val index = TechniqueIndex(game, walk.target.walker.techniqueSetVersion, walk.days.last(), entries)
        val path = File(dir, "${game.value}-index.txt").path
        io.writeText(path, TechniqueIndex.write(index))
        val readBack = io.readText(path)?.let { TechniqueIndex.read(it) }
        check(readBack == DecodeResult.Ok(index)) { "$path does not read back as the index that was written: $readBack" }
        val skips = settled.filterIsInstance<Settlement.Skip>().groupingBy { it.kind }.eachCount()
        val skipText = SkipKind.entries.joinToString(", ") { "$it ${skips[it] ?: 0}" }
        out("$game: #${numbers.first()}..#${numbers.last()} (${walk.days.first()}..${walk.days.last()}), ok ${entries.size}, skipped ${skips.values.sum()} ($skipText) -> $path")
    }

    companion object {
        /** The usage line of `index`. */
        const val USAGE: String = "puzzle-cli index --out <dir> --checkpoint <file> [--until <yyyy-mm-dd>] [--pace <seconds>]"

        /** Wait after every fetch: the app's own LinkedIn budget is 6 calls a minute (`LinkedInCallBudget`, core:data). */
        val PACE: Duration = 10.seconds

        /** Throttled or unavailable fetches in a row that stop the run (ADR-0017 "repeated throttles"). */
        const val MAX_FAILURES_IN_A_ROW: Int = 3
    }
}
