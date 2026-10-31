package com.ivandsky.puzzlebox.designsystem

import com.airbnb.android.showkase.annotation.ShowkaseRoot
import com.airbnb.android.showkase.annotation.ShowkaseRootModule

/**
 * Showkase trial root (spec A9/A10 — keep-or-drop decided after the run). Aggregates
 * this module's auto-indexed @Preview composables so `Showkase.getBrowserIntent(context)`
 * can launch the browser that ships in the showkase AAR (its activity merges into the
 * app manifest; no app-side registration — ticket 00 findings). Debug source set:
 * release builds carry neither the browser runtime nor this module.
 */
@ShowkaseRoot
class PuzzleBoxShowkaseModule : ShowkaseRootModule
