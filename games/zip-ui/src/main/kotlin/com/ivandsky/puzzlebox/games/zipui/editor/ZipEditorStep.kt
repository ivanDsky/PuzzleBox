package com.ivandsky.puzzlebox.games.zipui.editor

/**
 * The stages of authoring a Zip board. [tabs] picks the ones the editor shows: Path, Numbers and
 * Walls, or with the one-mode editor Path and [NumbersAndWalls]. Each [label] is the tab's text.
 */
enum class ZipEditorStep(val label: String) {
    Path("Path"),
    Numbers("Numbers"),
    Walls("Walls"),
    NumbersAndWalls("Numbers & walls"),
    ;

    companion object {
        /** The steps in the order the tabs show them. */
        fun tabs(combined: Boolean): List<ZipEditorStep> =
            if (combined) listOf(Path, NumbersAndWalls) else listOf(Path, Numbers, Walls)
    }
}
