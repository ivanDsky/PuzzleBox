package com.ivandsky.puzzlebox.history.ranks

import com.ivandsky.puzzlebox.data.visibility.OwnTier
import com.ivandsky.puzzlebox.data.visibility.VisibilityTier
import com.ivandsky.puzzlebox.gameui.board.DailyBoardRowModel
import com.ivandsky.puzzlebox.gameui.board.DailyBoardState
import com.ivandsky.puzzlebox.testing.repository.FakeVisibilityRepository
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The replay opt-in sheet at the view-model seam (A16, A20): once, only on a never-chosen tier, only with an official row. */
class ReplayOptInViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val visibility = FakeVisibilityRepository()
    private val viewModel by lazy { ReplayOptInViewModel(visibility) }

    private fun TestScope.collecting() {
        viewModel.show.launchIn(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
    }

    @Test
    fun `shows when the tier was never chosen and the viewer's official row exists`() = runTest {
        collecting()
        visibility.emitOwn(OwnTier.Unset)
        viewModel.rankedRow(true)

        assertTrue(viewModel.show.value)
    }

    @Test
    fun `never shows while the tier is unknown, so a cold start has no flash`() = runTest {
        collecting()
        viewModel.rankedRow(true)

        assertFalse(viewModel.show.value)
    }

    @Test
    fun `waits for the ranked row`() = runTest {
        collecting()
        visibility.emitOwn(OwnTier.Unset)

        assertFalse(viewModel.show.value)
        viewModel.rankedRow(true)
        assertTrue(viewModel.show.value)
    }

    @Test
    fun `a chosen tier never shows it`() = runTest {
        collecting()
        visibility.emitOwn(OwnTier.Chosen(VisibilityTier.Duel))
        viewModel.rankedRow(true)

        assertFalse(viewModel.show.value)
    }

    @Test
    fun `sharing sets Replays and the sheet never returns`() = runTest {
        collecting()
        visibility.emitOwn(OwnTier.Unset)
        viewModel.rankedRow(true)

        viewModel.share()

        assertEquals(listOf(VisibilityTier.Replays), visibility.setCalls)
        assertFalse(viewModel.show.value)
        visibility.emitOwn(OwnTier.Unset)
        assertFalse(viewModel.show.value)
    }

    @Test
    fun `keeping private sets Board and the sheet never returns`() = runTest {
        collecting()
        visibility.emitOwn(OwnTier.Unset)
        viewModel.rankedRow(true)

        viewModel.keepPrivate()

        assertEquals(listOf(VisibilityTier.Board), visibility.setCalls)
        assertFalse(viewModel.show.value)
        viewModel.rankedRow(false)
        viewModel.rankedRow(true)
        assertFalse(viewModel.show.value)
    }

    @Test
    fun `only an official row of the viewer counts as ranked`() {
        fun board(vararg rows: DailyBoardRowModel, pinned: DailyBoardRowModel? = null) = DailyBoardState.Ranked(rows.toList(), pinned = pinned)
        fun row(self: Boolean, unofficial: Boolean = false) = DailyBoardRowModel(rank = 1, name = "x", timeMs = 1_000, hints = 0, isSelf = self, playerId = "x", unofficial = unofficial)

        assertTrue(board(row(self = true)).hasOfficialOwnRow())
        assertTrue(board(row(self = false), pinned = row(self = true)).hasOfficialOwnRow())
        assertFalse(board(row(self = true, unofficial = true)).hasOfficialOwnRow())
        assertFalse(board(row(self = false)).hasOfficialOwnRow())
        assertFalse(DailyBoardState.Loading.hasOfficialOwnRow())
    }
}
