package com.ivandsky.puzzlebox.games.zipui.editor

/**
 * The stages of authoring a Zip board, in the order the tabs show them: Path, then the one Numbers & walls mode.
 * Each [label] is the tab's text.
 */
enum class ZipEditorStep(val label: String) {
    Path("Path"),
    NumbersAndWalls("Numbers & walls"),
}
