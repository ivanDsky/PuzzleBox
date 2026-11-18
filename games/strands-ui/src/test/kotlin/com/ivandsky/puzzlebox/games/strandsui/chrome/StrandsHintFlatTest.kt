package com.ivandsky.puzzlebox.games.strandsui.chrome

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.LocalFlatStrands
import com.ivandsky.puzzlebox.designsystem.LocalFlatSurfaces
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.games.strandsui.HintUi
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** With flat surfaces (the shared button's look, whatever FlatStrands says) the Tall hint button has no 4dp lip, so the row under the board reserves 64dp, not 68dp. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StrandsHintFlatTest {

    @get:Rule
    val rule = createComposeRule()

    private fun show(hint: HintUi?, flat: Boolean, strands: Boolean = flat) {
        rule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                CompositionLocalProvider(LocalFlatSurfaces provides flat, LocalFlatStrands provides strands) {
                    StrandsHintRow(found = 1, total = 6, hint = hint, onHint = {}, reduceMotion = true, placement = HintRowPlacement.Slots)
                }
            }
        }
    }

    @Test
    fun `flat on the hint row is 64dp tall`() {
        show(HintUi(count = 1, meterThirds = 2), flat = true)
        rule.onNodeWithTag("strands.hint_row").assertHeightIsEqualTo(64.dp)
    }

    @Test
    fun `flat on the row keeps 64dp once the board is solved and the hint is gone`() {
        show(hint = null, flat = true)
        rule.onNodeWithTag("strands.hint_row").assertHeightIsEqualTo(64.dp)
    }

    @Test
    fun `flat off the hint row stays 68dp tall`() {
        show(HintUi(count = 1, meterThirds = 2), flat = false)
        rule.onNodeWithTag("strands.hint_row").assertHeightIsEqualTo(68.dp)
    }

    @Test
    fun `flat surfaces on with flat strands off the hint row is 64dp tall`() {
        show(HintUi(count = 1, meterThirds = 2), flat = true, strands = false)
        rule.onNodeWithTag("strands.hint_row").assertHeightIsEqualTo(64.dp)
    }

    @Test
    fun `flat strands on with flat surfaces off the hint row stays 68dp tall`() {
        show(HintUi(count = 1, meterThirds = 2), flat = false, strands = true)
        rule.onNodeWithTag("strands.hint_row").assertHeightIsEqualTo(68.dp)
    }
}
