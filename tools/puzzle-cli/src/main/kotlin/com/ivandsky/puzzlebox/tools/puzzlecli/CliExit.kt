package com.ivandsky.puzzlebox.tools.puzzlecli

/** Process exit codes for `puzzle-cli validate` (§9.2): 0 ⇔ unique with no publish block. */
object CliExit {
    const val PUBLISHABLE: Int = 0 // verdict == "unique" && blocks.isEmpty()
    const val NOT_PUBLISHABLE: Int = 1 // parsed, but any other verdict or any block
    const val INVALID_TEXT: Int = 2 // "malformed": too large, bad header, unknown game, unsupported version, parse error
    const val USAGE: Int = 64 // bad arguments or unreadable file (message on err, no JSON)
}
