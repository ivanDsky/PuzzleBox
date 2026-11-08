package com.ivandsky.puzzlebox.games.tango.editor

import com.ivandsky.puzzlebox.game.editor.Difficulty.Level
import com.ivandsky.puzzlebox.game.editor.Difficulty.Level.Easy
import com.ivandsky.puzzlebox.game.editor.Difficulty.Level.Hard
import com.ivandsky.puzzlebox.game.editor.Difficulty.Level.Medium
import com.ivandsky.puzzlebox.game.editor.LadderRung

/**
 * The forced-move ladder of [com.ivandsky.puzzlebox.games.tango.engine.TangoRules.hint] as
 * [LadderRung]s, in difficulty order: the hardest rung a solve uses is the last one declared.
 * Expert is unused for Tango.
 */
enum class TangoRung(
    override val id: String,
    override val level: Level,
    override val captionKey: String,
) : LadderRung {
    MARKER_BINDING("marker", Easy, "tango.rung.marker"),
    THREE_IN_A_ROW("triple", Medium, "tango.rung.triple"),
    LINE_FULL("count", Hard, "tango.rung.count"),
    LINE_FILLS("lineFills", Hard, "tango.rung.lineFills"),
}
