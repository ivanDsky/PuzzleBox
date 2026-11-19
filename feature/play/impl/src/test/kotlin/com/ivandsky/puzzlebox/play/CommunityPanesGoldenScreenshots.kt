package com.ivandsky.puzzlebox.play

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.ivandsky.puzzlebox.data.model.PostState
import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.EmptyState
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.play.domain.CustomBoardView
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.community.CommunityActions
import com.ivandsky.puzzlebox.play.community.CommunityPhase
import com.ivandsky.puzzlebox.play.community.CommunityRowModel
import com.ivandsky.puzzlebox.play.community.CommunityState
import com.ivandsky.puzzlebox.play.board.BoardSignIn
import com.ivandsky.puzzlebox.play.friend.FriendCard as FriendCardState
import com.ivandsky.puzzlebox.play.friend.FriendCardAction
import com.ivandsky.puzzlebox.play.friend.FriendPuzzleCard
import com.ivandsky.puzzlebox.play.friend.PreviewBoardAuthor
import com.ivandsky.puzzlebox.play.friend.StubGivensBoard
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The tablet's Community tab, light and dark: the three tabs in the list pane and, on the right, the page of the
 * current tab's pick, each tab's default being its first puzzle: New today's first row ("selected"), the first friend
 * ("friends"), your first published puzzle ("yours"). Yours with nothing published ("yours-empty") and a New today
 * with nothing in it ("empty") show the empty pane. Goldens in `snapshots/community-panes/`. The phone's tabs are in
 * [CommunityTabsGoldenScreenshots].
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_TABLET)
class CommunityPanesGoldenScreenshotsTablet : CommunityPanesGoldenScreenshots("tablet") {
    @Test fun `selected light`() = panes("selected", LOADED, FRIENDS, yours = null, tab = null, dark = false)
    @Test fun `selected dark`() = panes("selected", LOADED, FRIENDS, yours = null, tab = null, dark = true)

    @Test fun `friends light`() = panes("friends", LOADED, FRIENDS, yours = null, tab = FRIENDS_TAB, dark = false)
    @Test fun `friends dark`() = panes("friends", LOADED, FRIENDS, yours = null, tab = FRIENDS_TAB, dark = true)

    @Test fun `yours light`() = panes("yours", LOADED, FRIENDS, yours = PUBLISHED, tab = YOURS_TAB, dark = false)
    @Test fun `yours dark`() = panes("yours", LOADED, FRIENDS, yours = PUBLISHED, tab = YOURS_TAB, dark = true)

    @Test fun `yours empty light`() = panes("yours-empty", LOADED, FRIENDS, yours = null, tab = YOURS_TAB, dark = false, yoursCount = 0)
    @Test fun `yours empty dark`() = panes("yours-empty", LOADED, FRIENDS, yours = null, tab = YOURS_TAB, dark = true, yoursCount = 0)

    @Test fun `empty light`() = panes("empty", EMPTY, emptyList(), yours = null, tab = null, dark = false, yoursCount = 0)
    @Test fun `empty dark`() = panes("empty", EMPTY, emptyList(), yours = null, tab = null, dark = true, yoursCount = 0)
}

abstract class CommunityPanesGoldenScreenshots(private val device: String) {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    /**
     * The tablet's panes over [state], [friends] and your published puzzle [yours] (a code, or null for none): the
     * tabs on the left, tapping [tab] first when it is not New today, and on the right the pick's page or the empty
     * message.
     */
    internal fun panes(
        name: String,
        state: CommunityState,
        friends: List<FriendCardModel>,
        yours: String?,
        tab: String?,
        dark: Boolean,
        yoursCount: Int = 3,
    ) {
        val summary = YoursSummary(count = yoursCount, firstPublishedCode = yours)
        rule.showGolden(dark) {
            CommunityPanes(
                default = { page -> defaultCommunityPick(page, state.phase, friends, summary) },
                list = { size, picks ->
                    CommunityTabs(
                        state = state,
                        actions = CommunityActions(onOpen = {}, onReport = {}, onGame = {}, onDifficulty = {}, onClearFilters = {}, onRetry = {}),
                        friends = friends,
                        custom = previewYoursSlot(yoursCount, yours),
                        onOpen = {},
                        onRemove = {},
                        onEnterCode = {},
                        modifier = size,
                        picks = picks,
                    )
                },
                empty = { page -> CommunityEmptyPane(page, state.phase) },
                detail = { pick, _ -> PickPage(pick, state, friends) },
            )
        }
        if (tab != null) {
            rule.onNodeWithTag(tab).performClick()
            rule.mainClock.advanceTimeBy(1_000)
            rule.onNodeWithTag(tab).assertIsSelected()
        }
        rule.settleAndCapture("community-panes", name, device, dark)
    }

    /**
     * The right pane's stand-in for the real page ([FriendPuzzleRoute] over its view model): a friend puzzle's card as
     * the picked row says (game, brand, title and author from the New today or Friends row), with the stub board the
     * page draws; or your published one. The real page's board is tested in
     * FriendHiddenBoardTest.
     */
    @Composable
    private fun PickPage(pick: CommunityPick, state: CommunityState, friends: List<FriendCardModel>) {
        if (pick is CommunityPick.Published) {
            EmptyState("Your published puzzle", "Page of ${pick.code}")
            return
        }
        val code = checkNotNull(ShareCode.parse(pick.code))
        val newToday = (state.phase as? CommunityPhase.Loaded)?.rows?.firstOrNull { it.code == code }
        val friend = friends.firstOrNull { it.row.code == code }
        val game = newToday?.game ?: friend?.gameName ?: PAGE.gameName
        val brand = newToday?.brand ?: friend?.brand ?: PAGE.brand
        val title = newToday?.title ?: friend?.row?.title ?: PAGE.puzzle.title
        val author = newToday?.author ?: friend?.row?.authorName ?: PAGE.puzzle.authorName
        FriendPuzzleCard(
            card = PAGE.copy(
                puzzle = PAGE.puzzle.copy(code = code, title = title, authorName = author),
                gameName = game,
                brand = brand,
                givens = StubGivensBoard().givens(Puzzle(PAGE.puzzle.gameId, PAGE.puzzle.variant, 1, ByteArray(0))),
            ),
            onAction = {},
            onSignIn = {},
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(bottom = Spacing.xl),
        )
    }

    internal companion object {
        const val FRIENDS_TAB = "community.tab.friends"
        const val YOURS_TAB = "community.tab.yours"
        const val PUBLISHED = "Z3Y8QD"
        val ZIP = GameBrand(AccentSlot.Blue, GameGlyphs.Zip)
        val TANGO = GameBrand(AccentSlot.Orange, GameGlyphs.Tango)
        val QUEENS = GameBrand(AccentSlot.Violet, GameGlyphs.Queens)

        /** The preview friends but the first, whose code is New today's first row: one code opens one puzzle. */
        val FRIENDS = FriendPreviewCards.drop(1)

        fun row(code: String, game: String, brand: GameBrand, title: String, level: Difficulty.Level?, age: String) =
            CommunityRowModel(checkNotNull(ShareCode.parse(code)), game, brand, title, author = "Maya", difficulty = level, age = age)

        val BASE = CommunityState()

        val LOADED = BASE.copy(
            phase = CommunityPhase.Loaded(
                listOf(
                    row("K7Q2MX", "Zip", ZIP, "Spiral staircase", Difficulty.Level.Hard, "5 min ago"),
                    row("P4R8NT", "Tango", TANGO, "Sun and moon", Difficulty.Level.Expert, "2 h ago"),
                    row("W9C3HB", "Queens", QUEENS, "Crown jewels", null, "13 h ago"),
                ),
            ),
        )
        val EMPTY = BASE.copy(phase = CommunityPhase.Loaded(emptyList()))

        /** The page of the first row: its puzzle, fresh, with Play. */
        val PAGE = FriendCardState(
            puzzle = ReceivedPuzzle(
                code = checkNotNull(ShareCode.parse("K7Q2MX")),
                gameId = GameId("zip"),
                variant = VariantId("zip/n6"),
                sharedFormatVersion = 1,
                puzzleBytes = ByteArray(0),
                title = "Spiral staircase",
                authorName = "Maya",
                note = "Start in the corner and go in.",
                authorTimeMs = 102_000L,
                authorHints = 0,
                authorUid = "uid-maya",
                openedAt = Instant.EPOCH,
                unpublished = false,
                postState = PostState.Posted,
                postAttempts = 0,
                difficulty = Difficulty.Level.Hard,
            ),
            gameName = "Zip",
            brand = ZIP,
            size = "6×6",
            action = FriendCardAction.Play,
            yours = null,
            board = CustomBoardView.SignedOut(PreviewBoardAuthor),
            signIn = BoardSignIn.Idle,
        )
    }
}
