package com.ivandsky.puzzlebox.benchmark

import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import java.util.regex.Pattern

/** Ranks' When chip, always on screen: "When" with nothing picked, else the picked window. */
internal val RANKS_WHEN_CHIP: BySelector = By.text(Pattern.compile("When|Week|Month"))

/**
 * One top-level tab: its nav label and a selector that is on screen once its root has composed,
 * with or without data, signed out and offline.
 */
internal enum class BenchmarkTab(val label: String, val ready: BySelector) {
    Today("Today", By.text("Recent")),
    Games("Games", By.text("Dailies")),
    Ranks("Ranks", RANKS_WHEN_CHIP),
    Community("Community", By.text("New today")),
}
