package com.ivandsky.puzzlebox.app.e2e

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.play.api.CommunityKey
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.data.match.CreateResult
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.Match
import com.ivandsky.puzzlebox.data.match.MatchDraft
import com.ivandsky.puzzlebox.data.match.MatchLeg
import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.domain.GetPuzzleDay
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.games.zip.ZipPuzzleRef
import com.ivandsky.puzzlebox.history.api.HistoryRootKey
import com.ivandsky.puzzlebox.home.api.HomeKey
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.play.api.ResultKey
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The one-line summaries on the real shell (SC2, SC7), on a phone's bar and a tablet's
 * rail: each opens its owner tab at its root and shows the owner's block, and Back from that
 * root returns to the screen the row was tapped on (Today). The sweep pins one owner per block: off its owner no
 * screen draws the
 * game page's board (`leaderboard.*`) or History's stats (`history.stats*`), and no screen
 * shows more than one `summary.<type>` row of a type.
 */
@RunWith(RobolectricTestRunner::class)
// DrainTestApplication (plain Application + looper drain): the merged manifest's PuzzleBoxApplication would start its own Koin.
@Config(sdk = [34], application = DrainTestApplication::class)
class ShellSummariesE2ETest {

    @Test
    @Config(qualifiers = PHONE)
    fun `on a phone Today's rank opens the Ranks hub, back to Today`() = rank()

    @Test
    @Config(qualifiers = RAIL)
    fun `on a tablet Today's rank opens the Ranks hub, back to Today`() = rank()

    @Test
    @Config(qualifiers = PHONE)
    fun `on a phone Today's duels row opens Games on Duels, back to Today`() = duels()

    @Test
    @Config(qualifiers = RAIL)
    fun `on a tablet Today's duels row opens Games on Duels, back to Today`() = duels()

    @Test
    @Config(qualifiers = PHONE)
    fun `on a phone each block shows only on its owner and each summary at most once`() = sweep()

    @Test
    @Config(qualifiers = RAIL)
    fun `on a tablet each block shows only on its owner and each summary at most once`() = sweep()

    private fun rank() = shellRobot(graph = rankedGraph()) {
        seedBoards()
        assertRoute(HOME)
        tap(RANK_ROW)

        assertRoute(RANKS)
        back()
        assertRoute(HOME)
        // Ranks stays as the link left it; Today is back with its stack untouched.
        assertEquals(HomeKey, state.tab)
        assertEquals(null, state.returnTo)
        assertEquals(listOf<NavKey>(HomeKey), state.currentStack)
    }

    private fun duels() = shellRobot(graph = shellGraph()) {
        seedDuelNeedingMe()
        assertRoute(HOME)
        tap(DUELS_ROW)

        awaitTag("duels.tab")
        rule.onNodeWithTag("library.tab.duels").assertIsSelected()
        assertRoute(LIBRARY)
        assertState(tabAt(PlayKey).copy(returnTo = NavigationState.Return(HomeKey, depth = 1)))
        back()
        assertRoute(HOME)
        assertState(tabAt(HomeKey))
    }

    private fun sweep() = shellRobot(tabAt(PlayKey, todaysResult()), graph = rankedGraph()) {
        seedBoards()
        seedDuelNeedingMe()
        assertRoute(RESULT)
        awaitTag("result.up")
        assertOwnersOnly()

        switchTab(HomeKey)
        assertRoute(HOME)
        awaitTag(RANK_ROW)
        awaitTag(DUELS_ROW)
        assertOwnersOnly()

        tapTab(HistoryRootKey)
        assertRoute(RANKS)
        assertOwnersOnly(historyOwner = true)

        tapTab(CommunityKey)
        assertRoute(COMMUNITY)
        assertOwnersOnly()

        tapTab(HomeKey)
        tapAvatar()
        assertRoute(PROFILE)
        assertOwnersOnly()
        back()

        tapAvatar()
        tapGear()
        assertRoute(SETTINGS)
        assertOwnersOnly()
    }

    /**
     * No Games-owned board on this screen, nor History-owned stats unless it is [historyOwner],
     * and at most one `summary.<type>` of each type.
     */
    private fun ShellRobot.assertOwnersOnly(historyOwner: Boolean = false) {
        val tags = rule.onAllNodes(hasAnyTag, useUnmergedTree = true).fetchSemanticsNodes()
            .map { it.config[SemanticsProperties.TestTag] }
        // The hub lands on Global until the player has a friend (#1178), so its own board rows carry rank medals; those are not Games' block.
        val gamesBoard = tags.filter { it.startsWith("leaderboard.") && !(historyOwner && it.startsWith("leaderboard.medal.")) }
        assertEquals("Games' board blocks off Games (state $state)", emptyList<String>(), gamesBoard)
        if (!historyOwner) {
            assertEquals("History's stats off History (state $state)", emptyList<String>(), tags.filter { it.startsWith("history.stats") })
        }
        val repeated = tags.filter { it.startsWith("summary.") }.groupingBy { it }.eachCount().filterValues { it > 1 }
        assertEquals("summary rows shown more than once (state $state)", emptyMap<String, Int>(), repeated)
    }

    /** Today's Zip daily solved, signed out. */
    private fun solvedGraph() = shellGraph().apply {
        records.setRecords(
            listOf(CompletionRecord(ZipPuzzleRef(PuzzleKey.Daily(E2E_TODAY, ZIP_NUMBER)), TIME_MS, 0, RecordStats(1, "{}"))),
        )
    }

    /** [solvedGraph], signed in: Today's rank row then needs only the board. */
    private fun rankedGraph() = solvedGraph().apply {
        identity.currentIdentity = Identity(uid = ME, displayName = ME_NAME, photoUrl = null)
    }

    /** The player on Zip's board for the shell's Today (the game page) and for the puzzle day (Today's rank). */
    private fun ShellRobot.seedBoards() {
        val puzzleDay = graph.koin.get<GetPuzzleDay>()()
        setOf(E2E_TODAY, puzzleDay).forEach { day ->
            graph.leaderboard.setEntries(
                LeaderboardRef(ZIP, day),
                listOf(LeaderboardEntry(ME, ME_NAME, "$ZIP/$day/$ZIP_NUMBER", TIME_MS, hints = 0, completedAtMs = 0)),
            )
        }
    }

    /** A friend's duel inviting the player: one duel needs them. */
    private fun ShellRobot.seedDuelNeedingMe() = runBlocking {
        graph.matches.now = Instant.now()
        graph.matches.signedInUid = FRIEND
        val draft = MatchDraft(
            code = DUEL_CODE,
            deadlineHours = Match.DEADLINE_CHOICES.first(),
            challenger = Player(FRIEND, FRIEND_NAME, photoUrl = null),
            invitedUid = ME,
            legs = listOf(MatchLeg(LegRef.Daily(ZipDefinition.id, E2E_TODAY, ZIP_NUMBER), authorUid = null)),
            includeFriends = false,
            rematchOf = null,
        )
        check(graph.matches.create(draft) is CreateResult.Created) { "the duel was not created" }
        graph.identity.currentIdentity = Identity(uid = ME, displayName = ME_NAME, photoUrl = null)
        graph.matches.signedInUid = ME
    }

    private fun todaysResult() = ResultKey(ZIP, E2E_TODAY.toString(), timeMs = TIME_MS, hintsUsed = 0)

    private companion object {
        const val PHONE = "w411dp-h891dp"
        const val RAIL = "w1280dp-h800dp-land"
        const val ZIP = "zip"
        const val ZIP_NUMBER = 592
        const val TIME_MS = 61_000L
        const val ME = "me"
        const val ME_NAME = "Me"
        const val FRIEND = "sam"
        const val FRIEND_NAME = "Sam"
        val DUEL_CODE = checkNotNull(ShareCode.parse("K7Q2MX"))
        const val RANK_ROW = "summary.leaderboard"
        const val DUELS_ROW = "summary.duels"
        const val HOME = "route.home"
        const val LIBRARY = "route.library"
        const val RANKS = "route.ranks"
        const val COMMUNITY = "route.community"
        const val RESULT = "route.result"
        const val PROFILE = "route.profile"
        const val SETTINGS = "route.settings"
        val hasAnyTag = SemanticsMatcher("any test tag") { it.config.getOrNull(SemanticsProperties.TestTag) != null }
    }
}
