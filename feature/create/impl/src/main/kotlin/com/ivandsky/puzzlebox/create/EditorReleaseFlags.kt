package com.ivandsky.puzzlebox.create

import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.model.GameId

/** Which [ReleaseFlag] gates each game's editor on the New puzzle sheet; ids with no entry are always offered. */
internal object EditorReleaseFlags {
    private val gates: Map<GameId, ReleaseFlag> = mapOf(
        GameId("zip") to ReleaseFlag.ZipEditor,
        GameId("tango") to ReleaseFlag.TangoEditor,
        GameId("minisudoku") to ReleaseFlag.SudokuEditor,
        GameId("sudoku") to ReleaseFlag.SudokuEditor,
        GameId("strands") to ReleaseFlag.StrandsEditor,
    )

    /** null = not gated (offered whenever the game has an editor). */
    fun flagFor(id: GameId): ReleaseFlag? = gates[id]
}
