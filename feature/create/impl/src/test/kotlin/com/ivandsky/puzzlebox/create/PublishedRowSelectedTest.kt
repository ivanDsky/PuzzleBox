package com.ivandsky.puzzlebox.create

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
import com.ivandsky.puzzlebox.designsystem.LocalSelectedRowCode
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A published row of yours draws as the picked one, like New today's and a friend's, while it is the puzzle the
 * Community two-pane shows ([LocalSelectedRowCode]); any other row, and a list outside the panes, are not selected.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PublishedRowSelectedTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `the row whose code is picked is selected`() {
        show(picked = "K7Q2MX")

        rule.onNodeWithTag("create.published.row").assertIsSelected()
    }

    @Test
    fun `a row of another code is not selected`() {
        show(picked = "M3P8RT")

        rule.onNodeWithTag("create.published.row").assertIsNotSelected()
    }

    @Test
    fun `outside the panes no row is selected`() {
        show(picked = null)

        rule.onNodeWithTag("create.published.row").assertIsNotSelected()
    }

    private fun show(picked: String?) {
        rule.setContent {
            PuzzleBoxTheme {
                CompositionLocalProvider(LocalSelectedRowCode provides picked) {
                    PublishedRow(
                        row = CreateUiState.PublishedRow(
                            code = checkNotNull(ShareCode.parse("K7Q2MX")),
                            gameId = GameId("zip"),
                            brand = GameBrand(AccentSlot.Blue, GameGlyphs.Zip),
                            title = "Sunday special",
                            meta = "Author 1:42",
                            published = "published 2 d ago",
                        ),
                        onOpen = {},
                    )
                }
            }
        }
    }
}
