package com.ivandsky.puzzlebox.history

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import com.ivandsky.puzzlebox.data.repository.DailyEntry
import com.ivandsky.puzzlebox.designsystem.MaxContentWidth
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.history.api.HistoryKey
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** On a wide window the History tab root (header and list) stops at the max content width, centred, as Create's does. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-land-mdpi")
class HistoryRootWidthTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `the tab root is capped and centred, the header on the list's left edge`() {
        val viewModel = HistoryTestSupport.viewModel(
            FakeDailyRepository().apply { setHistory(HistoryTestSupport.ZIP, emptyList<DailyEntry>()) },
            FakeCompletionRepository(emptyList()),
            emptySet(),
        )
        rule.setContent {
            PuzzleBoxTheme {
                HistoryRoute(
                    key = HistoryKey(),
                    layout = HistoryLayout.SinglePane,
                    onBack = null,
                    onPlayAgain = { _, _ -> },
                    viewModel = viewModel,
                )
            }
        }
        rule.waitUntil(5_000) {
            rule.onAllNodesWithTag("history.header").fetchSemanticsNodes().isNotEmpty()
        }

        val root = rule.onRoot().getBoundsInRoot()
        val list = rule.onNodeWithTag("history.list").getBoundsInRoot()
        val header = rule.onNodeWithTag("history.header").getBoundsInRoot()
        assertEquals(MaxContentWidth.value, (list.right - list.left).value, 0.5f)
        assertEquals((list.left - root.left).value, (root.right - list.right).value, 0.5f)
        assertEquals(list.left.value, header.left.value, 0.5f)
        assertEquals(list.right.value, header.right.value, 0.5f)
    }
}
