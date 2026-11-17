package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.EmptyState
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.community.CommunityActions
import com.ivandsky.puzzlebox.play.community.CommunityPhase
import com.ivandsky.puzzlebox.play.community.GameChoice
import com.ivandsky.puzzlebox.play.friend.FriendPuzzleCard
import com.ivandsky.puzzlebox.play.friend.StubGivensBoard

/** The tag of the Duels stand-in's page, for the strips' page position. */
internal const val TAB_SWIPE_DUELS = "strip.duels"

/**
 * Every strip of the two main devices: today and option A in all four scenarios, B on its taps, C on its swipes and
 * the far tap, D on the far tap, each light and dark; then the reduced-motion form and the switcher once.
 */
internal fun tabSwipeStrips(): List<Array<Any>> {
    val all = listOf("games-swipe", "games-back", "community-swipe", "community-far")
    val scenarios = listOf(
        "Today" to all,
        "A" to all,
        "B" to listOf("games-tap", "community-far"),
        "C" to listOf("games-swipe", "games-back", "community-far"),
        "D" to listOf("community-far"),
    )
    val moving = scenarios.flatMap { (option, names) ->
        names.flatMap { name -> listOf("light", "dark").map { theme -> "${option}_${name}_${theme}_motion" } }
    }
    val rest = listOf(
        "Today_games-swipe_light_reduced",
        "Today_community-far_light_reduced",
        "A_games-swipe_light_reduced",
        "A_community-far_light_reduced",
        "A_switcher_light_motion",
        "A_switcher_dark_motion",
    )
    return (moving + rest).map { arrayOf<Any>(it) }
}

/** Today only, for the other orientation of each device. */
internal fun tabSwipeTodayStrips(): List<Array<Any>> =
    listOf("games-swipe", "games-back", "community-swipe", "community-far").map { arrayOf<Any>("Today_${it}_light_motion") }

/** Games as production has it with the Community tab on: Dailies and Duels, the Duels tab and pane as stand-ins. */
@Composable
internal fun TabSwipeGames(layout: PlayLayout) {
    LibraryScreen(
        state = previewState(),
        page = null,
        month = null,
        layout = layout,
        hasLesson = { true },
        onSelect = {},
        onPlay = { _, _ -> },
        onRetry = {},
        onOpenLesson = {},
        calendar = CalendarActions(),
        onOpenRanks = { _, _ -> },
        friends = FriendPreviewCards,
        onEnterCode = {},
        duels = TabSwipeDuels,
        custom = null,
        communityTab = true,
    )
}

/** Community: its three tabs alone on one pane, or in the tablet's panes with each tab's first puzzle on the right. */
@Composable
internal fun TabSwipeCommunity(layout: PlayLayout) {
    val state = CommunityPanesGoldenScreenshots.LOADED.copy(
        games = listOf(GameChoice(GameId("zip"), "Zip"), GameChoice(GameId("tango"), "Tango"), GameChoice(GameId("queens"), "Queens")),
    )
    val friends = CommunityPanesGoldenScreenshots.FRIENDS
    val published = CommunityPanesGoldenScreenshots.PUBLISHED
    val actions = CommunityActions(onOpen = {}, onReport = {}, onGame = {}, onDifficulty = {}, onClearFilters = {}, onRetry = {})
    if (layout == PlayLayout.SinglePane) {
        CommunityTabs(state, actions, friends, previewYoursSlot(YOURS), onOpen = {}, onRemove = {}, onEnterCode = {}, modifier = Modifier.fillMaxSize())
        return
    }
    val summary = YoursSummary(count = YOURS, firstPublishedCode = published)
    CommunityPanes(
        default = { page -> defaultCommunityPick(page, state.phase, friends, summary) },
        list = { size, picks ->
            CommunityTabs(state, actions, friends, previewYoursSlot(YOURS, published), onOpen = {}, onRemove = {}, onEnterCode = {}, modifier = size, picks = picks)
        },
        empty = { page -> CommunityEmptyPane(page, state.phase) },
        detail = { pick, _ ->
            if (pick is CommunityPick.Published) {
                EmptyState("Your published puzzle", "Page of ${pick.code}")
            } else {
                // The golden's stand-in for the real page: the picked row's card over the stub board.
                val code = checkNotNull(ShareCode.parse(pick.code))
                val row = (state.phase as? CommunityPhase.Loaded)?.rows?.firstOrNull { it.code == code }
                val friend = friends.firstOrNull { it.row.code == code }
                val page = CommunityPanesGoldenScreenshots.PAGE
                FriendPuzzleCard(
                    card = page.copy(
                        puzzle = page.puzzle.copy(
                            code = code,
                            title = row?.title ?: friend?.row?.title ?: page.puzzle.title,
                            authorName = row?.author ?: friend?.row?.authorName ?: page.puzzle.authorName,
                        ),
                        gameName = row?.game ?: friend?.gameName ?: page.gameName,
                        brand = row?.brand ?: friend?.brand ?: page.brand,
                        givens = StubGivensBoard().givens(Puzzle(page.puzzle.gameId, page.puzzle.variant, 1, ByteArray(0))),
                    ),
                    onAction = {},
                    onSignIn = {},
                    modifier = Modifier.verticalScroll(rememberScrollState()).padding(bottom = Spacing.xl),
                )
            }
        },
    )
}

private const val YOURS = 3

/** The underline goldens' stand-in for the compete module's tab, with a tag on its page. */
private val TabSwipeDuels = DuelsSlot(
    content = { _, _, _ ->
        Column(
            Modifier.fillMaxSize().testTag(TAB_SWIPE_DUELS).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            repeat(3) { index ->
                Surface(Modifier.fillMaxWidth().height(96.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.large) {
                    Text("Duel ${index + 1}", Modifier.padding(Spacing.lg))
                }
            }
        }
    },
    needYou = { 3 },
    onOpenDuel = {},
    pane = { modifier -> DuelPaneStandIn(modifier) },
)
