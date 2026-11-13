package com.ivandsky.puzzlebox.app.e2e

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.create.api.CreateKey
import com.ivandsky.puzzlebox.data.match.MatchResultPoster
import com.ivandsky.puzzlebox.app.play.ZipPlayFixture
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.match.ClaimResult
import com.ivandsky.puzzlebox.data.match.LegResult
import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.data.match.PostResult
import com.ivandsky.puzzlebox.data.match.ResultPostState
import com.ivandsky.puzzlebox.domain.DuelRowKind
import com.ivandsky.puzzlebox.domain.GetPuzzleDay
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.gameui.DragEvent
import com.ivandsky.puzzlebox.gameui.GridCell
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.games.zipui.ZipPlayController
import com.ivandsky.puzzlebox.history.api.HistoryKey
import com.ivandsky.puzzlebox.history.api.HistoryRootKey
import com.ivandsky.puzzlebox.home.api.HomeKey
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.play.ActivePlay
import com.ivandsky.puzzlebox.play.api.MatchLegKey
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The user-check's second round, re-run over the real shell and the match fakes: create a Daily duel and press
 * Done (the row waits on a friend); open it as its challenger; the Created sheet's share text lists the
 * drawn puzzles' games and carries the code and Share; a friend claims and the countdown and the
 * header's "ends" time share the deadline; both finish and the header pills give way to the reveal.
 */
@RunWith(RobolectricTestRunner::class)
// Plain Application: the merged manifest's PuzzleBoxApplication would start its own Koin.
@Config(sdk = [34], application = DrainTestApplication::class, qualifiers = "w411dp-h891dp")
class DuelUserCheckE2ETest {

    private val graph = E2eKoinGraph(E2E_TODAY, releaseFlags = FakeReleaseFlags(ReleaseFlag.Competitions))

    @Test
    fun theRoundTwoWalkthroughReadsAsTheUserApprovedIt() = duel {
        val code = createDuel(ZIP)
        val match = checkNotNull(graph.matches.matches[code])

        // The Created sheet: the invite lists the games of the puzzles drawn, with the code, and offers Share.
        val games = match.legs.map { graph.koin.get<GameRegistry>().displayName(it.ref.gameId) }.distinct()
        val invite = textOf(hasAnyAncestor(hasTestTag(CREATED_TEXT)) and hasText("challenges you", substring = true))
        assertTrue("the invite names the best of: $invite", invite.contains("best of ${match.legs.size} — ${games.joinToString(", ")} ·"))
        assertTrue("the invite carries the code: $invite", invite.contains(code.display))
        assertTrue("the invite carries the link: $invite", invite.contains(code.matchLink))
        assertTrue("the sheet offers Share", shows(CREATED_SHARE))
        tap(CREATED_DONE)

        // Nobody has accepted, so the row waits on a friend, offers Share again, and needs nothing from me.
        val waiting = duels()
        assertEquals(DuelRowKind.Unclaimed, waiting.rows.single { it.code == code }.kind)
        assertEquals("a duel nobody accepted is not in the Duels badge", 0, waiting.needYou)
        assertFalse("the Duels pill counts nothing", shows(DUELS_COUNT))
        assertTrue("the row waits on a friend", textShown("Waiting for a friend"))
        assertTrue("the row offers Share again", textShown("Share again"))

        // Opened by its challenger, the invite reads as their own duel, waiting, with Cancel.
        tapDuelRow(code)
        awaitTag(INVITE_CODE)
        assertTrue(textShown("Your duel"))
        assertTrue(textShown("You challenged a friend"))
        assertFalse(textShown("Duel · from a friend"))
        assertTrue("the invite is in its waiting state", textShown("Waiting on a friend"))
        assertTrue("the challenger can cancel", shows(INVITE_CANCEL))
        assertFalse("nothing to play before a claim", shows(LEGS_TITLE))

        // Once a friend claims, the pill counts to the 24 h deadline, not closesAt (+6 h grace), and the header's
        // "ends" is the same instant; the minutes depend on the wall clock.
        friendClaims(code)
        awaitTag(LEGS_TITLE)
        rule.onNodeWithTag(TIME_LEFT).assert(hasText("Closes in 23 h ", substring = true))
        val ends = DateTimeFormatter.ofPattern("EEE HH:mm", Locale.getDefault()).format(match.deadline.atZone(ZoneId.systemDefault()))
        assertEquals("the deadline is a day after the create", Duration.ofHours(24), Duration.between(match.createdAt, match.deadline))
        rule.onNodeWithText("Best of ${match.legs.size} · ends $ends").assertExists()
        assertTrue("the score pill shows while playing", textShown(HIDDEN_SCORE))

        // Both sides finish every puzzle: the pills are gone and the footer offers the reveal.
        friendPlays(code)
        playMyLeg(code)
        tap(RESULT_UP)
        awaitTag(SEE_REVEAL)
        assertFalse("the countdown pill is gone", shows(TIME_LEFT))
        assertFalse("the hidden-score pill is gone", textShown(HIDDEN_SCORE))
        assertTrue("the footer reads Both finished", textShown("Both finished"))
        assertTrue("the footer offers the reveal", textShown("See results"))
    }

    /** Runs [block] on the Play tab, signed in as me, with today's Zip in place and the result poster running. */
    private fun duel(block: ShellRobot.() -> Unit) = shellRobot(onPlay, graph = graph) {
        graph.identity.currentIdentity = Identity(uid = ME, displayName = ME_NAME, photoUrl = null)
        graph.matches.now = Instant.now()
        graph.matches.signedInUid = ME
        val today = graph.koin.get<GetPuzzleDay>()()
        graph.calendar.setAll(today)
        graph.daily.setHistory(ZIP, listOf(ZipPlayFixture.entry(today, TODAY_NUMBER)))
        graph.daily.setCached(ZIP, today, ZipPlayFixture.envelope())
        graph.daily.setPuzzleNumbers(ZIP) { day -> TODAY_NUMBER.takeIf { day == today } }
        graph.koin.get<MatchResultPoster>().start()
        block()
    }

    /** The text of the one node [matcher] finds, joined. */
    private fun ShellRobot.textOf(matcher: SemanticsMatcher): String =
        rule.onNode(matcher).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString("") { it.text }

    private fun ShellRobot.textShown(text: String, substring: Boolean = false): Boolean =
        rule.onAllNodesWithText(text, substring = substring, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /** Solves my puzzle of [code] from its duel screen and waits for the poster to post it. */
    private fun ShellRobot.playMyLeg(code: ShareCode) {
        val leg = MatchLegKey(code.value, 0, ZIP.value)
        openDuelPuzzle(leg)
        solveDuelPuzzle(leg, ready = { it.zip.session != null }) { play ->
            ZipPlayFixture.solution.forEach { play.zip.onDragEvent(DragEvent.Extend(GridCell(it.idx / SIZE, it.idx % SIZE))) }
        }
        awaitIdling {
            runBlocking { graph.outbox.observeAll().first() }.any { it.code == code && it.state == ResultPostState.Posted }
        }
    }

    private val ActivePlay.zip: ZipPlayController get() = controller as ZipPlayController

    /** The friend, signed in on their own device, takes the second seat. */
    private fun friendClaims(code: ShareCode) = asFriend {
        check(graph.matches.claim(code, Player(FRIEND, FRIEND_NAME, photoUrl = null)) == ClaimResult.Claimed) { "the friend's claim failed" }
    }

    /** The friend solves the duel's puzzle and posts their time. */
    private fun friendPlays(code: ShareCode) = asFriend {
        val result = LegResult(FRIEND, 0, FRIEND_TIME_MS, 0, Instant.now().toEpochMilli(), gaveUp = false)
        check(graph.matches.post(code, result) == PostResult.Posted) { "the friend's post failed" }
    }

    private fun asFriend(block: suspend () -> Unit) = runBlocking {
        graph.matches.signedInUid = FRIEND
        try {
            block()
        } finally {
            graph.matches.signedInUid = ME
        }
    }

    /** The Play tab is selected; Home's stack holds History. */
    private val onPlay = NavigationState(
        tab = PlayKey,
        stacks = listOf(listOf(HomeKey, HistoryKey()), listOf(PlayKey), listOf(HistoryRootKey), listOf(CreateKey)),
    )

    private companion object {
        const val ME = "me"
        const val ME_NAME = "Me"
        const val FRIEND = "sam"
        const val FRIEND_NAME = "Sam"
        const val FRIEND_TIME_MS = 90_000L
        const val TODAY_NUMBER = 592
        const val SIZE = 5
        val ZIP = ZipDefinition.id

        const val RESULT_UP = "result.up"
        const val CREATED_TEXT = "duel.created.text"
        const val CREATED_SHARE = "duel.created.share"
        const val CREATED_DONE = "duel.created.done"
        const val INVITE_CODE = "duel.invite.code"
        const val INVITE_CANCEL = "duel.invite.cancel"
        const val LEGS_TITLE = "duel.legs.title"
        const val TIME_LEFT = "duel.legs.timeLeft"
        const val SEE_REVEAL = "duel.legs.reveal"
        const val DUELS_COUNT = "library.tab.duels.count"
        const val HIDDEN_SCORE = "Score hidden until it settles"
    }
}
