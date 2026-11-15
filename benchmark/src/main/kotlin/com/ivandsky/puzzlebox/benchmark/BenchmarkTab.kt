package com.ivandsky.puzzlebox.benchmark

import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector

/**
 * One top-level tab: its nav label and a selector that is on screen once its root has composed,
 * with or without data, signed out and offline.
 */
internal enum class BenchmarkTab(val label: String, val ready: BySelector) {
    Today("Today", By.text("Recent")),
    Games("Games", By.text("Dailies")),
    Ranks("Ranks", By.descStartsWith("Board ")),
    Create("Create", By.text("Make a puzzle for someone")),
}
