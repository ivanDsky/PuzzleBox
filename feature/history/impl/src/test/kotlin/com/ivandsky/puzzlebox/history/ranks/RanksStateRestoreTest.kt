package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.lifecycle.SavedStateHandle
import com.ivandsky.puzzlebox.designsystem.LocalTabReselect
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.ranks.RankScope
import com.ivandsky.puzzlebox.history.HistoryTestSupport.QUEENS
import com.ivandsky.puzzlebox.history.HistoryTestSupport.ZIP
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.board.DailyBoardState
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.repository.FakeLeaderboardRepository
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlinx.coroutines.flow.MutableSharedFlow

/** A8 / SC 10, 11: the hub's picks survive process death through a [SavedStateHandle]; a game's link opens the hub at that game and day. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class RanksStateRestoreTest {

    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val hub = RanksTestHub(FakeLeaderboardRepository(emptyMap()), null)
    private val day = LocalDate.of(2026, 11, 9)
    private val boards = rankBoards(hub.registry)

    private val picked = RanksSelection(
        board = RanksBoard.Game(QUEENS),
        window = RanksSelection.Window.Week,
        scope = RankScope.Friends,
        day = day,
        calendarExpanded = true,
        shownDay = LocalDate.of(2026, 10, 30),
    )

    @Test
    fun `board, window, scope, day and calendar round-trip through the handle`() {
        val handle = SavedStateHandle()
        RanksSelectionSaver(handle).save(picked)

        assertEquals(picked, RanksSelectionSaver(handle).restore(RanksSelection(), boards))
    }

    @Test
    fun `an unpicked day restores as null, so the board keeps following the rollover`() {
        val handle = SavedStateHandle()
        RanksSelectionSaver(handle).save(RanksSelection(board = RanksBoard.Game(ZIP)))

        val restored = RanksSelectionSaver(handle).restore(RanksSelection(), boards)

        assertNull(restored.day)
        assertNull(restored.shownDay)
        assertEquals(RanksBoard.Game(ZIP), restored.board)
    }

    @Test
    fun `an empty handle gives the fallback`() {
        val opening = RanksSelection(board = RanksBoard.Game(ZIP), day = day)

        assertEquals(opening, RanksSelectionSaver(SavedStateHandle()).restore(opening, boards))
    }

    @Test
    fun `a board that is no longer listed, or a corrupt value, gives the fallback`() {
        val gone = SavedStateHandle().also { RanksSelectionSaver(it).save(picked.copy(board = RanksBoard.Game(GameId("retired")))) }
        val corrupt = SavedStateHandle().also {
            RanksSelectionSaver(it).save(picked)
            it[DAY_KEY] = "not-a-date"
        }

        assertEquals(RanksSelection(), RanksSelectionSaver(gone).restore(RanksSelection(), boards))
        assertEquals(RanksSelection(), RanksSelectionSaver(corrupt).restore(RanksSelection(), boards))
    }

    @Test
    fun `a view model writes every change to the handle and a new one restores it`() {
        val handle = SavedStateHandle()
        val first = hub.viewModel(savedState = handle)

        first.pickBoard(RanksBoard.Game(QUEENS))
        first.setScope(RankScope.Friends)
        first.setWindow(RanksSelection.Window.Month)
        first.selectDayForTest(day)

        val second = hub.viewModel(savedState = handle)
        assertEquals(first.selection.value, second.selection.value)
        assertEquals(RanksBoard.Game(QUEENS), second.selection.value.board)
        assertEquals(RankScope.Friends, second.selection.value.scope)
        assertEquals(RanksSelection.Window.Month, second.selection.value.window)
        assertEquals(day, second.selection.value.day)
    }

    @Test
    fun `a game's link opens the hub at that game and day`() {
        val viewModel = hub.viewModel(opening = RanksSelection.opening(RanksBoard.Game(ZIP), day))

        assertEquals(RanksBoard.Game(ZIP), viewModel.selection.value.board)
        assertEquals(day, viewModel.selection.value.day)
        assertEquals(RanksSelection.Window.Day, viewModel.selection.value.window)
    }

    @Test
    fun `saved picks win over the opening link after process death`() {
        val handle = SavedStateHandle()
        RanksSelectionSaver(handle).save(picked)

        val viewModel = hub.viewModel(savedState = handle, opening = RanksSelection.opening(RanksBoard.Game(ZIP), LocalDate.of(2026, 11, 1)))

        assertEquals(picked, viewModel.selection.value)
    }

    @Test
    fun `a link without a day opens at today`() {
        val viewModel = hub.viewModel(opening = RanksSelection.opening(RanksBoard.Game(ZIP), null))

        assertNull(viewModel.selection.value.day)
    }

    @Test
    fun `reset returns to the landing state and the handle follows`() {
        val handle = SavedStateHandle()
        val viewModel = hub.viewModel(savedState = handle)
        viewModel.select { picked }

        viewModel.resetSelection()

        assertEquals(RanksSelection(), viewModel.selection.value)
        assertEquals(RanksSelection(), RanksSelectionSaver(handle).restore(picked, boards))
    }

    @Test
    fun `a link to an unlisted game opens Overall at that day`() {
        val link = RanksSelection.opening(RanksBoard.Game(GameId("retired")), day).landing(boards, hub.calendar::today)

        val viewModel = hub.viewModel(opening = link)

        assertEquals(RanksBoard.Overall, viewModel.selection.value.board)
        assertEquals(day, viewModel.selection.value.day)
    }

    @Test
    fun `a link to today opens with day null and the calendar at today`() {
        val link = RanksSelection.opening(RanksBoard.Game(ZIP), hub.today).landing(boards, hub.calendar::today)

        val viewModel = hub.viewModel(opening = link)

        assertEquals(RanksBoard.Game(ZIP), viewModel.selection.value.board)
        assertNull(viewModel.selection.value.day)
        assertNull(viewModel.selection.value.shownDay)
    }

    @Test
    fun `a day is judged by its own game's today, not another game's`() {
        hub.calendar.set(ZIP, day)

        val zip = RanksSelection.opening(RanksBoard.Game(ZIP), day).landing(boards, hub.calendar::today)
        val queens = RanksSelection.opening(RanksBoard.Game(QUEENS), day).landing(boards, hub.calendar::today)

        assertNull(zip.day)
        assertEquals(day, queens.day)
    }

    private fun RanksViewModel.selectDayForTest(day: LocalDate) = select { it.pickFromCalendar(day) }

    @Test
    fun `a tab reselect scrolls to the top first and resets only from the top`() {
        val reselect = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        var resets = 0
        composeRule.setContent {
            PuzzleBoxTheme {
                CompositionLocalProvider(LocalTabReselect provides reselect) {
                    RanksScreen(RanksUiState(RanksBoardSlice("Overall · today", RankOrder.TimeThenHints, DailyBoardState.Loading)), RanksActions(onSignIn = {}, onSwipe = {}, onReset = { resets++ }), modifier = Modifier.height(60.dp))
                }
            }
        }
        composeRule.onNodeWithTag("route.ranks").performTouchInput { swipeUp() }
        composeRule.waitForIdle()

        reselect.tryEmit(Unit)
        composeRule.waitForIdle()
        assertEquals("first reselect only scrolls", 0, resets)

        reselect.tryEmit(Unit)
        composeRule.waitForIdle()
        assertEquals("second reselect resets", 1, resets)
    }
}
