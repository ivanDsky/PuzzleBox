package com.ivandsky.puzzlebox.play

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.ivandsky.puzzlebox.data.model.PostState
import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
import com.ivandsky.puzzlebox.designsystem.SecondaryButton
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.domain.CustomBoardView
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.community.CommunityActions
import com.ivandsky.puzzlebox.play.community.CommunityPhase
import com.ivandsky.puzzlebox.play.community.CommunityRowModel
import com.ivandsky.puzzlebox.play.community.CommunityState
import com.ivandsky.puzzlebox.play.community.communitySection
import com.ivandsky.puzzlebox.play.board.BoardSignIn
import com.ivandsky.puzzlebox.play.friend.FriendCard as FriendCardState
import com.ivandsky.puzzlebox.play.friend.FriendCardAction
import com.ivandsky.puzzlebox.play.friend.FriendPuzzleCard
import com.ivandsky.puzzlebox.play.friend.PreviewBoardAuthor
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Community tab, light and dark: on the phone the list under its head (New today rows, a Make a puzzle row of the
 * app's, From friends rows), and on the tablet Games' two-pane with the list on the left and the picked puzzle's page on
 * the right, either with the first puzzle selected by default or, with nothing new today and no friend puzzle, the
 * empty message. Goldens in `snapshots/community-panes/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class CommunityPanesGoldenScreenshotsPhone : CommunityPanesGoldenScreenshots("phone") {
    @Test fun `list light`() = list("list", LOADED, FRIENDS, dark = false)
    @Test fun `list dark`() = list("list", LOADED, FRIENDS, dark = true)
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_TABLET)
class CommunityPanesGoldenScreenshotsTablet : CommunityPanesGoldenScreenshots("tablet") {
    @Test fun `selected light`() = panes("selected", LOADED, FRIENDS, dark = false)
    @Test fun `selected dark`() = panes("selected", LOADED, FRIENDS, dark = true)

    @Test fun `empty light`() = panes("empty", EMPTY, emptyList(), dark = false)
    @Test fun `empty dark`() = panes("empty", EMPTY, emptyList(), dark = true)
}

abstract class CommunityPanesGoldenScreenshots(private val device: String) {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    /** The phone's list: [CommunityList] as the tab lays it out, with a slot like the app's. */
    internal fun list(name: String, state: CommunityState, friends: List<FriendCardModel>, dark: Boolean) {
        rule.showGolden(dark) { TabList(state, friends, phone = true, Modifier.fillMaxSize()) }
        rule.settleAndCapture("community-panes", name, device, dark)
    }

    /** The tablet's panes: the list pane and, on the right, the first puzzle's page or the empty message. */
    internal fun panes(name: String, state: CommunityState, friends: List<FriendCardModel>, dark: Boolean) {
        rule.showGolden(dark) {
            CommunityPanes(
                default = defaultCommunityPick(state.phase, friends),
                list = { size -> TabList(state, friends, phone = false, size) },
                empty = { CommunityEmptyPane(state.phase) },
                detail = { _, _ ->
                    FriendPuzzleCard(
                        card = PAGE,
                        onAction = {},
                        onSignIn = {},
                        modifier = Modifier.verticalScroll(rememberScrollState()).padding(bottom = Spacing.xl),
                    )
                },
            )
        }
        rule.settleAndCapture("community-panes", name, device, dark)
    }

    @Composable
    private fun TabList(state: CommunityState, friends: List<FriendCardModel>, phone: Boolean, modifier: Modifier) {
        val picker = LocalCommunityPicker.current
        val actions = CommunityActions(
            onOpen = { picker?.puzzle(it.value) },
            onReport = {},
            onGame = {},
            onDifficulty = {},
            onClearFilters = {},
            onRetry = {},
        )
        val slot = CustomSlot { listState, _, friendsRows ->
            LazyColumn(Modifier.fillMaxSize(), state = listState) {
                communitySection(state, actions)
                item(key = "make") {
                    Box(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md)) {
                        SecondaryButton(text = "Make a puzzle", onClick = {})
                    }
                }
                friendsRows()
            }
        }
        CommunityList(
            custom = slot,
            listState = rememberLazyListState(),
            friends = friends,
            phone = phone,
            onOpen = {},
            onRemove = {},
            onEnterCode = {},
            modifier = modifier,
        )
    }

    internal companion object {
        val ZIP = GameBrand(AccentSlot.Blue, GameGlyphs.Zip)
        val TANGO = GameBrand(AccentSlot.Orange, GameGlyphs.Tango)
        val QUEENS = GameBrand(AccentSlot.Violet, GameGlyphs.Queens)

        /** The preview friends but the first, whose code is New today's first row: one code opens one puzzle. */
        val FRIENDS = FriendPreviewCards.drop(1)

        fun row(code: String, game: String, brand: GameBrand, title: String, level: String?, age: String) =
            CommunityRowModel(checkNotNull(ShareCode.parse(code)), game, brand, title, author = "Maya", difficulty = level, age = age)

        val BASE = CommunityState(enabled = true)

        val LOADED = BASE.copy(
            phase = CommunityPhase.Loaded(
                listOf(
                    row("K7Q2MX", "Zip", ZIP, "Spiral staircase", "Hard", "5 min ago"),
                    row("P4R8NT", "Tango", TANGO, "Sun and moon", "Expert", "2 h ago"),
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
