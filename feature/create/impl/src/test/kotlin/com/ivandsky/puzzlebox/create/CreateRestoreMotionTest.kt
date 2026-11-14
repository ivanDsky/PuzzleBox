package com.ivandsky.puzzlebox.create

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.model.ShareCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What Create does when the first cloud restore ends with items: the restoring card fades out while the restored
 * list fades in, on the screen and on a tab, so the swap is not a hard one. A Create that was never restoring changes
 * nothing. Behavior only, on a paused clock; the fade is the screen's own crossfade, one length for every swap, and
 * its reduced-motion length is the existing short fade.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CreateRestoreMotionTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var state by mutableStateOf(CreateUiState())

    private val game = PreviewGames.first()
    private val draft = CreateUiState.DraftRow("d1", game.id, game.brand, "A draft", game.name, "edited 1 h ago", Verdict.Checking)
    private val published = CreateUiState.PublishedRow(
        code = checkNotNull(ShareCode.parse("K7Q2MX")),
        gameId = game.id,
        brand = game.brand,
        title = "A puzzle",
        meta = "You 1:00",
        published = "published 1 h ago",
    )

    private fun show(initial: CreateUiState) {
        state = initial
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            PuzzleBoxTheme {
                CreateScreen(
                    state, onNew = { _, _ -> }, onOpen = {}, onQuery = {}, onFilter = {}, onTab = {}, onOpenPublished = {},
                    onDuplicate = {}, onDelete = {}, onUndoDelete = {},
                )
            }
        }
        composeRule.mainClock.advanceTimeBy(SETTLE_MS)
    }

    private fun change(next: CreateUiState) {
        composeRule.runOnUiThread { state = next }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeByFrame()
    }

    private fun exists(tag: String) =
        composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `the restoring card is still fading out a frame after the restored drafts arrive`() {
        show(CreateUiState(loading = false, games = PreviewGames, restoring = true))
        assertTrue("the restoring card is shown before the restore ends", exists("create.restoring"))

        change(state.copy(drafts = listOf(draft), draftCount = 1, restoring = false))

        assertTrue("the restored draft is on show", exists("create.draft.row"))
        assertTrue("the restoring card fades out, it is not swapped at once", exists("create.restoring"))
        composeRule.mainClock.advanceTimeBy(SETTLE_MS)
        assertTrue("the restoring card is gone once it has faded", !exists("create.restoring"))
    }

    @Test
    fun `a tab's restoring card is still fading out a frame after its restored list arrives`() {
        show(
            CreateUiState(
                loading = false, games = PreviewGames, drafts = listOf(draft), draftCount = 1,
                tab = CreateUiState.Tab.Published, restoring = true,
            ),
        )
        assertTrue("the tab says it is restoring", exists("create.restoring"))

        change(state.copy(published = listOf(published), restoring = false))

        assertTrue("the restored puzzle is on show", exists("create.published.row"))
        assertTrue("the restoring card fades out, it is not swapped at once", exists("create.restoring"))
        composeRule.mainClock.advanceTimeBy(SETTLE_MS)
        assertTrue("the restoring card is gone once it has faded", !exists("create.restoring"))
    }

    @Test
    fun `a Create that was never restoring swaps its empty state for the drafts at once`() {
        show(CreateUiState(loading = false, games = PreviewGames))
        assertTrue("the first-time state is shown", exists("create.empty"))

        change(state.copy(drafts = listOf(draft), draftCount = 1))

        assertTrue("the draft is on show", exists("create.draft.row"))
        assertTrue("the first-time state leaves at once, as before", !exists("create.empty"))
    }

    @Test
    fun `the fade is the medium one, and the existing short fade under reduced motion`() {
        assertEquals(MotionTokens.DurationMedium, createFadeMillis(reduced = false))
        assertEquals(MotionTokens.ReducedFadeDuration, createFadeMillis(reduced = true))
    }

    private companion object {
        const val SETTLE_MS = 2_000L
    }
}
