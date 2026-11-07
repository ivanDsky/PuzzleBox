package com.ivandsky.puzzlebox.create

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.DpRect
import com.ivandsky.puzzlebox.designsystem.MaxContentWidth
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.editor.Verdict
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** On a wide window the Create root's lists stop at the max content width, centred. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-land-mdpi")
class CreateRootWidthTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val drafts = PreviewGames.mapIndexed { index, game ->
        CreateUiState.DraftRow("d$index", game.id, game.brand, "Untitled $index", game.name, "edited 1 h ago", Verdict.Checking)
    }

    @Test
    fun `the draft list is capped and centred`() {
        show(CreateUiState(loading = false, drafts = drafts, draftCount = drafts.size, games = PreviewGames))
        assertCentredAtMax(rule.onNodeWithTag("create.list").getBoundsInRoot())
    }

    @Test
    fun `the first-time game list is capped and centred`() {
        show(CreateUiState(loading = false, games = PreviewGames))
        assertCentredAtMax(rule.onNodeWithTag("create.empty").getBoundsInRoot())
    }

    private fun show(state: CreateUiState) {
        rule.setContent {
            PuzzleBoxTheme {
                CreateScreen(state, onNew = { _, _ -> }, onOpen = {}, onQuery = {}, onFilter = {}, onTab = {}, onOpenPublished = {}, onDuplicate = {}, onDelete = {}, onUndoDelete = {})
            }
        }
    }

    private fun assertCentredAtMax(bounds: DpRect) {
        val root = rule.onRoot().getBoundsInRoot()
        assertEquals(MaxContentWidth.value, (bounds.right - bounds.left).value, 0.5f)
        assertEquals((bounds.left - root.left).value, (root.right - bounds.right).value, 0.5f)
    }
}
