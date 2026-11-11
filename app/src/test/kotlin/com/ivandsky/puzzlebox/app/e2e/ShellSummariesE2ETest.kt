package com.ivandsky.puzzlebox.app.e2e

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithTag
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.create.api.CreateKey
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
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.play.api.ResultKey
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The one-line summaries on the real shell (SC2, SC7), on a phone's bar and a tablet's
 * rail: each opens its owner tab at its root and shows the owner's block, and Back from that
 * root returns to Today. The sweep pins one owner per block: off its owner no screen draws the
 * game page's board (`leaderboard.*`) or History's stats (`history.stats*`), and no screen
 * shows more than one `summary.<type>` row of a type.
 */
@RunWith(RobolectricTestRunner::class)
// Plain Application: the merged manifest's PuzzleBoxApplication would start its own Koin.
@Config(sdk = [34], application = android.app.Application::class)
class ShellSummariesE2ETest {

    @Test
    @Config(qualifiers = PHONE)
    fun `on a phone Today's rank opens the game's page and its board, back to Games then Today`() = rank(Width.Phone)

    @Test
    @Config(qualifiers = RAIL)
    fun `on a tablet Today's rank opens the game's page and its board, back to Today`() = rank(Width.Rail)

    @Test
    @Config(qualifiers = PHONE)
    fun `on a phone Today's duels row opens Games on Duels, back to Today`() = duels()

    @Test
    @Config(qualifiers = RAIL)
    fun `on a tablet Today's duels row opens Games on Duels, back to Today`() = duels()

    @Test
    @Config(qualifiers = PHONE)
    fun `on a phone the game page's history row opens the History root, back to Today`() = history()

    @Test
    @Config(qualifiers = RAIL)
    fun `on a tablet the game page's history row opens the History root, back to Today`() = history()

    @Test
    @Config(qualifiers = PHONE)
    fun `on a phone Result's Full board opens the game's page and leaves the Result behind`() = fullBoard()

    @Test
    @Config(qualifiers = RAIL)
    fun `on a tablet Result's Full board opens the game's page and leaves the Result behind`() = fullBoard()

    @Test
    @Config(qualifiers = PHONE)
    fun `on a phone Full board from a Result played on Today leaves no Result behind`() = fullBoardFromToday(Width.Phone)

    @Test
    @Config(qualifiers = RAIL)
    fun `on a tablet Full board from a Result played on Today leaves no Result behind`() = fullBoardFromToday(Width.Rail)

    @Test
    @Config(qualifiers = PHONE)
    fun `on a phone each block shows only on its owner and each summary at most once`() = sweep()

    @Test
    @Config(qualifiers = RAIL)
    fun `on a tablet each block shows only on its owner and each summary at most once`() = sweep()

    @Test
    @Config(qualifiers = PHONE)
    fun `on a phone Today's See all opens the History root, back to Today`() = seeAll()

    @Test
    @Config(qualifiers = RAIL)
    fun `on a tablet Today's See all opens the History root, back to Today`() = seeAll()

    private fun seeAll() = shellRobot {
        assertRoute(HOME)
        tapText("See all")

        assertRoute(HISTORY)
        assertState(tabAt(HistoryRootKey))
        back()
        assertRoute(HOME)
        assertState(tabAt(HomeKey))
    }

    private fun rank(width: Width) = shellRobot(graph = rankedGraph()) {
        seedBoards()
        assertRoute(HOME)
        tap(RANK_ROW)

        awaitTag(ZIP_PAGE)
        awaitTag("daily_board.title")
        assertState(tabAt(PlayKey))
        if (width == Width.Phone) {
            back()
            assertRoute(LIBRARY)
            assertFalse("the page closed onto the list", shows(ZIP_PAGE))
            assertState(tabAt(PlayKey))
        }
        back()
        assertRoute(HOME)
        assertState(tabAt(HomeKey))
    }

    private fun duels() = shellRobot(graph = shellGraph(ReleaseFlag.Competitions)) {
        seedDuelNeedingMe()
        assertRoute(HOME)
        tap(DUELS_ROW)

        awaitTag("duels.tab")
        rule.onNodeWithTag("library.tab.duels").assertIsSelected()
        assertRoute(LIBRARY)
        assertState(tabAt(PlayKey))
        back()
        assertRoute(HOME)
        assertState(tabAt(HomeKey))
    }

    private fun history() = shellRobot {
        tapTab(PlayKey)
        tap("library.row.$ZIP")
        awaitTag(ZIP_PAGE)
        tap("summary.history")

        assertRoute(HISTORY)
        assertState(tabAt(HistoryRootKey))
        assertFalse("the History root has no back icon", shows("history.back"))
        back()
        assertRoute(HOME)
        assertState(tabAt(HomeKey))
    }

    // Signed out: the row needs only today's official solve, which the record store holds.
    private fun fullBoard() = shellRobot(tabAt(PlayKey, todaysResult()), graph = solvedGraph()) {
        assertRoute(RESULT)
        tap(RANK_ROW)

        awaitTag(ZIP_PAGE)
        assertRoute(LIBRARY)
        assertState(tabAt(PlayKey))
        assertTrue("no stack holds the Result", state.stacks.flatten().none { it is ResultKey })
    }

    // Played from Today: the Result sits on Home's stack, which opening Games does not reset.
    private fun fullBoardFromToday(width: Width) = shellRobot(tabAt(HomeKey, todaysResult()), graph = solvedGraph()) {
        assertRoute(RESULT)
        tap(RANK_ROW)

        awaitTag(ZIP_PAGE)
        assertRoute(LIBRARY)
        assertState(tabAt(PlayKey))
        assertTrue("no stack holds the Result: $state", state.stacks.flatten().none { it is ResultKey })
        if (width == Width.Phone) {
            back()
            assertRoute(LIBRARY)
            assertFalse("the page closed onto the list", shows(ZIP_PAGE))
        }
        back()
        assertRoute(HOME)
        assertState(tabAt(HomeKey))
    }

    private fun sweep() = shellRobot(tabAt(PlayKey, todaysResult()), graph = rankedGraph(ReleaseFlag.Competitions)) {
        seedBoards()
        seedDuelNeedingMe()
        assertRoute(RESULT)
        awaitTag(RANK_ROW)
        assertOwnersOnly()

        switchTab(HomeKey)
        assertRoute(HOME)
        awaitTag(RANK_ROW)
        awaitTag(DUELS_ROW)
        assertOwnersOnly()

        tapTab(HistoryRootKey)
        assertRoute(HISTORY)
        assertOwnersOnly(historyOwner = true)

        tapTab(CreateKey)
        assertRoute(CREATE)
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
        assertEquals("Games' board blocks off Games (state $state)", emptyList<String>(), tags.filter { it.startsWith("leaderboard.") })
        if (!historyOwner) {
            assertEquals("History's stats off History (state $state)", emptyList<String>(), tags.filter { it.startsWith("history.stats") })
        }
        val repeated = tags.filter { it.startsWith("summary.") }.groupingBy { it }.eachCount().filterValues { it > 1 }
        assertEquals("summary rows shown more than once (state $state)", emptyMap<String, Int>(), repeated)
    }

    /** Today's Zip daily solved, signed out. */
    private fun solvedGraph(vararg also: ReleaseFlag) = shellGraph(*also).apply {
        records.setRecords(
            listOf(CompletionRecord(ZipPuzzleRef(PuzzleKey.Daily(E2E_TODAY, ZIP_NUMBER)), TIME_MS, 0, RecordStats(1, "{}"))),
        )
    }

    /** [solvedGraph], signed in: Today's rank row then needs only the board. */
    private fun rankedGraph(vararg also: ReleaseFlag) = solvedGraph(*also).apply {
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

    private enum class Width { Phone, Rail }

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
        const val ZIP_PAGE = "library.page.$ZIP"
        const val RANK_ROW = "summary.leaderboard"
        const val DUELS_ROW = "summary.duels"
        const val HOME = "route.home"
        const val LIBRARY = "route.library"
        const val HISTORY = "route.history"
        const val CREATE = "route.create"
        const val RESULT = "route.result"
        const val PROFILE = "route.profile"
        const val SETTINGS = "route.settings"
        val hasAnyTag = SemanticsMatcher("any test tag") { it.config.getOrNull(SemanticsProperties.TestTag) != null }
    }
}
