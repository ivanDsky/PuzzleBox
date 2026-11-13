package com.ivandsky.puzzlebox.create

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollTo
import com.ivandsky.puzzlebox.designsystem.LocalTabReselect
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.model.ShareCode
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A tab reselect on the Create root scrolls the list on screen back to its top. Only the
 * shown list is composed, so one reselect reaches one list: the drafts, the published tab,
 * or the first-time empty state.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h400dp")
class CreateReselectTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val drafts = (0 until 12).map { index ->
        val game = PreviewGames[index % PreviewGames.size]
        CreateUiState.DraftRow("d$index", game.id, game.brand, "Untitled $index", game.name, "edited 1 h ago", Verdict.Checking)
    }

    private val published = (0 until 12).map { index ->
        val game = PreviewGames[index % PreviewGames.size]
        CreateUiState.PublishedRow(
            code = checkNotNull(ShareCode.parse("K7Q2%02d".format(index))),
            gameId = game.id,
            brand = game.brand,
            title = "Published $index",
            meta = game.name,
            published = "published 1 d ago",
        )
    }

    @Test
    fun `a tab reselect scrolls the drafts list back to its first draft`() {
        val reselect = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        show(CreateUiState(loading = false, drafts = drafts, draftCount = drafts.size, published = published, games = PreviewGames), reselect)
        rule.onNodeWithTag("create.list").performScrollToIndex(11)
        rule.onNodeWithText("Untitled 0").assertIsNotDisplayed()

        rule.runOnIdle { reselect.tryEmit(Unit) }

        rule.onNodeWithText("Untitled 0").assertIsDisplayed()
    }

    @Test
    fun `a tab reselect scrolls the published list back to its first puzzle`() {
        val reselect = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        show(
            CreateUiState(loading = false, drafts = drafts, draftCount = drafts.size, published = published, games = PreviewGames, tab = CreateUiState.Tab.Published),
            reselect,
        )
        rule.onNodeWithTag("create.published.list").performScrollToIndex(11)
        rule.onNodeWithText("Published 0").assertIsNotDisplayed()

        rule.runOnIdle { reselect.tryEmit(Unit) }

        rule.onNodeWithText("Published 0").assertIsDisplayed()
    }

    @Test
    fun `a tab reselect scrolls the first-time empty state back to its top`() {
        val reselect = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val games = PreviewGames + PreviewGames + PreviewGames
        rule.setContent {
            CompositionLocalProvider(LocalTabReselect provides reselect) {
                PuzzleBoxTheme {
                    CreateEmptyState(games = games, onStart = { _, _ -> }, onImport = {})
                }
            }
        }
        rule.onNodeWithText("Import from text").performScrollTo()
        rule.onNodeWithText("Make a puzzle for someone").assertIsNotDisplayed()

        rule.runOnIdle { reselect.tryEmit(Unit) }

        rule.onNodeWithText("Make a puzzle for someone").assertIsDisplayed()
    }

    private fun show(state: CreateUiState, reselect: SharedFlow<Unit>) {
        rule.setContent {
            CompositionLocalProvider(LocalTabReselect provides reselect) {
                PuzzleBoxTheme {
                    CreateScreen(state, onNew = { _, _ -> }, onOpen = {}, onQuery = {}, onFilter = {}, onTab = {}, onOpenPublished = {}, onDuplicate = {}, onDelete = {}, onUndoDelete = {})
                }
            }
        }
    }
}
