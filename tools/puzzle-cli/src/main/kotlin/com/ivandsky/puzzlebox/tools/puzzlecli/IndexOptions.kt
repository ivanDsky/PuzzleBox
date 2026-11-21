package com.ivandsky.puzzlebox.tools.puzzlecli

import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The `index` flags: [out] the directory for the index files, [checkpoint] the resume file, [until] the
 * last day to walk, [pace] the wait after every fetch.
 */
class IndexOptions(val out: String, val checkpoint: String, val until: LocalDate?, val pace: Duration) {
    companion object {
        /** The fastest `--pace` accepted: one fetch every five seconds. */
        const val MIN_PACE_SECONDS: Int = 5

        /**
         * The options [args] (everything after `index`) spell.
         *
         * @throws IllegalArgumentException with the reason when a flag is unknown, repeated, valueless
         *   or missing, `--until` is not a `yyyy-mm-dd` date, or `--pace` is not a whole number of
         *   seconds of at least [MIN_PACE_SECONDS]
         */
        fun parse(args: List<String>): IndexOptions {
            val values = HashMap<String, String>()
            var at = 0
            while (at < args.size) {
                val flag = args[at]
                require(flag in FLAGS) { "unknown argument '$flag'" }
                require(flag !in values) { "$flag given twice" }
                values[flag] = args.getOrNull(at + 1) ?: throw IllegalArgumentException("$flag needs a value")
                at += 2
            }
            val until = values["--until"]?.let {
                try {
                    LocalDate.parse(it)
                } catch (bad: DateTimeParseException) {
                    throw IllegalArgumentException("--until is not a yyyy-mm-dd date: '$it'", bad)
                }
            }
            val pace = values["--pace"]?.let {
                val seconds = it.toIntOrNull()
                require(seconds != null && seconds >= MIN_PACE_SECONDS) { "--pace must be a whole number of seconds, at least $MIN_PACE_SECONDS: '$it'" }
                seconds.seconds
            } ?: IndexCommand.PACE
            return IndexOptions(
                out = requireNotNull(values["--out"]) { "--out is required" },
                checkpoint = requireNotNull(values["--checkpoint"]) { "--checkpoint is required" },
                until = until,
                pace = pace,
            )
        }

        private val FLAGS = setOf("--out", "--checkpoint", "--until", "--pace")
    }
}
