package com.ivandsky.puzzlebox.data.testing

import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.games.zip.ZipPuzzleRef
import java.time.LocalDate

/**
 * Bridge for app-layer tests: record refs are always ZipPuzzleRefs (the only
 * record-eligible game), but app modules deliberately never depend on games/zip —
 * they construct refs through this seam instead.
 */
fun testZipRef(day: LocalDate, puzzleNumber: Int? = null): PuzzleRef =
    ZipPuzzleRef(day, puzzleNumber)
