package com.ivandsky.puzzlebox.tools.puzzlecli

/**
 * Process exit codes for `puzzle-cli`: `validate` (§9.2) is 0 ⇔ unique with no publish block; `index` is 0 when done;
 * `generate-drills` is 0 when every short technique got its drills.
 */
object CliExit {
    const val PUBLISHABLE: Int = 0 // verdict == "unique" && blocks.isEmpty()
    const val NOT_PUBLISHABLE: Int = 1 // parsed, but any other verdict or any block; `generate-drills`: a technique stayed short
    const val INVALID_TEXT: Int = 2 // "malformed": too large, bad header, unknown game, unsupported version, parse error
    const val STOPPED: Int = 3 // `index` stopped after repeated throttles; rerun the same command to resume
    const val USAGE: Int = 64 // bad arguments or unreadable file (message on err, no JSON); `generate-drills`: no index file found
}
