package com.ivandsky.puzzlebox.train

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import org.junit.Rule

/**
 * Review renders of the Train pane (ADR-0033: rendered on demand, never committed): the technique list, a practice
 * board before a move, after a Right, after an undone Wrong move, finished with its hold running, the watching screen
 * and the chapter's result page. The board is a stand-in grid, as this module sees no game's board. Each render sits in
 * the padding the Games screen gives it: the phone's page inset, or the tablet's 400dp list column with the practice
 * board or the placeholder beside it.
 *
 * This is only the harness. Each area owns its own golden file (`Train<Area>GoldenScreenshots.kt`, a phone and a tablet
 * class), so a new screen adds a file here and edits none.
 */
abstract class TrainGoldenScreenshots(private val device: String) {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    internal fun capture(name: String, dark: Boolean, content: @Composable () -> Unit) {
        rule.showGolden(dark, content)
        rule.settleAndCapture("train", name, device, dark)
    }
}
