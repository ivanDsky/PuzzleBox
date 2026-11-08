package com.ivandsky.puzzlebox.games.zipui.editor

/** Zip editor hit-test sizes, as fractions of a cell. */
internal object ZipEditorDefaults {
    /** How far from the line between two cells a tap still hits the wall; wider than the default. */
    const val EDGE_BAND = 0.38f

    /** Dead length at each end of an edge: none, so a wall is hit along its whole side. */
    const val EDGE_INSET = 0f
}
