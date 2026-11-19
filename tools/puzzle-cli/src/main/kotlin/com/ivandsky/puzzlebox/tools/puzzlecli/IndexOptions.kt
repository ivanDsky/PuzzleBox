package com.ivandsky.puzzlebox.tools.puzzlecli

import java.time.LocalDate
import java.time.format.DateTimeParseException

/** The `index` flags: [out] the directory for the index files, [checkpoint] the resume file, [until] the last day to walk. */
class IndexOptions(val out: String, val checkpoint: String, val until: LocalDate?) {
    companion object {
        /**
         * The options [args] (everything after `index`) spell.
         *
         * @throws IllegalArgumentException with the reason when a flag is unknown, repeated, valueless
         *   or missing, or `--until` is not a `yyyy-mm-dd` date
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
            return IndexOptions(
                out = requireNotNull(values["--out"]) { "--out is required" },
                checkpoint = requireNotNull(values["--checkpoint"]) { "--checkpoint is required" },
                until = until,
            )
        }

        private val FLAGS = setOf("--out", "--checkpoint", "--until")
    }
}
