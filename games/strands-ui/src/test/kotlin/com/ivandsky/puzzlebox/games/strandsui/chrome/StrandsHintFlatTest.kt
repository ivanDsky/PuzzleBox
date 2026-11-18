package com.ivandsky.puzzlebox.games.strandsui.chrome

import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.games.strandsui.HintUi
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Strands hint control is flat (no 4dp lip), so the row under the board reserves 64dp, not 68dp. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StrandsHintFlatTest {

    @get:Rule
    val rule = createComposeRule()

    private fun show(hint: HintUi?) {
        rule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                StrandsHintRow(found = 1, total = 6, hint = hint, onHint = {}, reduceMotion = true, placement = HintRowPlacement.Slots)
            }
        }
    }

    @Test
    fun `the hint row is 64dp tall with no flag set`() {
        show(HintUi(count = 1, meterThirds = 2))
        rule.onNodeWithTag("strands.hint_row").assertHeightIsEqualTo(64.dp)
    }

    @Test
    fun `the row keeps 64dp once the board is solved and the hint is gone`() {
        show(hint = null)
        rule.onNodeWithTag("strands.hint_row").assertHeightIsEqualTo(64.dp)
    }
}
