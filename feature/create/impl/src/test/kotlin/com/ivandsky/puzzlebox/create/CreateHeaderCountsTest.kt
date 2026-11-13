package com.ivandsky.puzzlebox.create

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithTag
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.model.ShareCode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Create header counts the drafts and the published puzzles under its title, once there are any. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CreateHeaderCountsTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val drafts = (0 until 3).map { index ->
        val game = PreviewGames[index % PreviewGames.size]
        CreateUiState.DraftRow("d$index", game.id, game.brand, "Untitled $index", game.name, "edited 1 h ago", Verdict.Checking)
    }

    private val published = (0 until 5).map { index ->
        val game = PreviewGames[index % PreviewGames.size]
        CreateUiState.PublishedRow(
            code = checkNotNull(ShareCode.parse("K7Q2M$index")),
            gameId = game.id,
            brand = game.brand,
            title = "Published $index",
            meta = game.name,
            published = "published 1 d ago",
        )
    }

    @Test
    fun `the subtitle carries the draft and published counts`() {
        show(CreateUiState(loading = false, drafts = drafts, draftCount = drafts.size, published = published, games = PreviewGames))
        val lines = header().onChildren()
        lines.assertCountEquals(2)
        lines[1].assert(hasText("${drafts.size}", substring = true)).assert(hasText("${published.size}", substring = true))
    }

    @Test
    fun `with nothing made yet the title stands alone`() {
        show(CreateUiState(loading = false, games = PreviewGames))
        header().onChildren().assertCountEquals(1)
    }

    @Test
    fun `while loading the title stands alone`() {
        show(CreateUiState(loading = true, draftCount = drafts.size, published = published, games = PreviewGames))
        header().onChildren().assertCountEquals(1)
    }

    private fun header() = rule.onNodeWithTag("create.header", useUnmergedTree = true)

    private fun show(state: CreateUiState) {
        rule.setContent {
            PuzzleBoxTheme {
                CreateScreen(state, onNew = { _, _ -> }, onOpen = {}, onQuery = {}, onFilter = {}, onTab = {}, onOpenPublished = {}, onDuplicate = {}, onDelete = {}, onUndoDelete = {})
            }
        }
    }
}
