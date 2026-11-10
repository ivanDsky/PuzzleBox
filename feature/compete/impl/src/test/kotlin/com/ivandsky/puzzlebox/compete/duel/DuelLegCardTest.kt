package com.ivandsky.puzzlebox.compete.duel

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.model.GameId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A finished puzzle whose result did not settle says why on its card, below my score — too late to
 * count, or saved on this device only. The model-level mapping is `DuelLegsModelTest`'s; this renders the card.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DuelLegCardTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun show(posting: PostingCaption?) {
        val row = LegRowModel(
            legIndex = 0,
            gameId = GameId("zip"),
            title = "Zip",
            rankOrder = RankOrder.TimeThenHints,
            status = LegStatus.Solved(timeMs = 61_000, hints = 0),
            opponent = OpponentLine.Sealed,
            posting = posting,
            action = null,
            actionEnabled = false,
        )
        rule.setContent { PuzzleBoxTheme { DuelLegCard(row, opponentName = "Sam", brand = GameBrand.Neutral) } }
    }

    private fun assertShown(text: String) = rule.onAllNodesWithText(text).assertCountEquals(1)

    @Test
    fun aLateResultSaysItCameTooLateToCount() {
        show(PostingCaption.Late)

        assertShown("Too late to count: the duel had closed")
    }

    @Test
    fun aLocalOnlyResultSaysItIsSavedOnThisDeviceOnly() {
        show(PostingCaption.LocalOnly)

        assertShown("Saved on this device only")
    }

    @Test
    fun aCountedResultCarriesNoCaption() {
        show(posting = null)

        listOf("Too late to count: the duel had closed", "Saved on this device only", "Sending your result…")
            .forEach { rule.onAllNodesWithText(it).assertCountEquals(0) }
    }
}
