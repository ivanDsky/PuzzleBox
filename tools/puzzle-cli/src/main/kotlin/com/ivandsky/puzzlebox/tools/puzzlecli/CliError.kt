package com.ivandsky.puzzlebox.tools.puzzlecli

import kotlinx.serialization.Serializable

/** One whole-text, 1-based error position (spec A23). */
@Serializable
data class CliError(val line: Int, val col: Int, val message: String)
