package com.ivandsky.puzzlebox.play

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.EmptyState
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.play.community.CommunityActions
import com.ivandsky.puzzlebox.play.community.CommunityState
import com.ivandsky.puzzlebox.play.community.DifficultyChoice
import com.ivandsky.puzzlebox.play.community.GameChoice
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The community filter as a sheet (backlog #1548), light and dark: New today's page with its filter bar, with a filter
 * in force ("page-filtered") and with the sheet open over it, nothing picked ("sheet") and Zip and Hard picked
 * ("sheet-picked"). The tablet shows the panes, so the sheet opens over the whole screen. Goldens in
 * `snapshots/community-filter-sheet/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class CommunityFilterSheetGoldenScreenshotsPhone : CommunityFilterSheetGoldenScreenshots("phone", tablet = false)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_TABLET)
class CommunityFilterSheetGoldenScreenshotsTablet : CommunityFilterSheetGoldenScreenshots("tablet", tablet = true)

abstract class CommunityFilterSheetGoldenScreenshots(private val device: String, private val tablet: Boolean) {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun `page light`() = capture("page", NOTHING, open = false, dark = false)
    @Test fun `page dark`() = capture("page", NOTHING, open = false, dark = true)

    @Test fun `page filtered light`() = capture("page-filtered", PICKED, open = false, dark = false)
    @Test fun `page filtered dark`() = capture("page-filtered", PICKED, open = false, dark = true)

    @Test fun `sheet light`() = capture("sheet", NOTHING, open = true, dark = false)
    @Test fun `sheet dark`() = capture("sheet", NOTHING, open = true, dark = true)

    @Test fun `sheet picked light`() = capture("sheet-picked", PICKED, open = true, dark = false)
    @Test fun `sheet picked dark`() = capture("sheet-picked", PICKED, open = true, dark = true)

    private fun capture(name: String, state: CommunityState, open: Boolean, dark: Boolean) {
        rule.showGolden(dark) { if (tablet) Panes(state) else Tabs(state, Modifier.fillMaxSize()) }
        if (open) {
            rule.onNodeWithTag("community.filter.button").performClick()
            rule.mainClock.advanceTimeBy(1_000)
            rule.onNodeWithTag("community.filter.sheet").assertExists()
        }
        rule.settleAndCapture("community-filter-sheet", name, device, dark)
    }

    @Composable
    private fun Tabs(state: CommunityState, modifier: Modifier, picks: CommunityTabPicks? = null) {
        CommunityTabs(
            state = state,
            actions = ACTIONS,
            friends = CommunityPanesGoldenScreenshots.FRIENDS,
            custom = previewYoursSlot(3),
            onOpen = {},
            onRemove = {},
            onEnterCode = {},
            modifier = modifier,
            picks = picks,
        )
    }

    @Composable
    private fun Panes(state: CommunityState) {
        val summary = YoursSummary(count = 3, firstPublishedCode = null)
        CommunityPanes(
            default = { page -> defaultCommunityPick(page, state.phase, CommunityPanesGoldenScreenshots.FRIENDS, summary) },
            list = { size, picks -> Tabs(state, size, picks) },
            empty = { page -> CommunityEmptyPane(page, state.phase) },
            detail = { pick, _ -> EmptyState("Puzzle page", pick.code) },
        )
    }

    private companion object {
        val ACTIONS = CommunityActions(onOpen = {}, onReport = {}, onGame = {}, onDifficulty = {}, onClearFilters = {}, onRetry = {})

        val GAMES = listOf(
            GameChoice(GameId("zip"), "Zip", GameBrand(AccentSlot.Blue, GameGlyphs.Zip)),
            GameChoice(GameId("tango"), "Tango", GameBrand(AccentSlot.Orange, GameGlyphs.Tango)),
            GameChoice(GameId("queens"), "Queens", GameBrand(AccentSlot.Violet, GameGlyphs.Queens)),
            GameChoice(GameId("strands"), "Strands", GameBrand(AccentSlot.Pink, GameGlyphs.Strands)),
            GameChoice(GameId("sudoku"), "Mini Sudoku", GameBrand(AccentSlot.Teal, GameGlyphs.Sudoku)),
        )

        val NOTHING = CommunityPanesGoldenScreenshots.LOADED.copy(games = GAMES, filterSheet = true)

        val PICKED = NOTHING.copy(game = GameId("zip"), difficulty = DifficultyChoice.Hard)
    }
}
