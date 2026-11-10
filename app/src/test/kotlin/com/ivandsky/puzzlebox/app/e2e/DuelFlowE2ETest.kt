package com.ivandsky.puzzlebox.app.e2e

import com.ivandsky.puzzlebox.data.match.MatchResultPoster
import com.ivandsky.puzzlebox.app.nav.topLevelDestinations
import com.ivandsky.puzzlebox.app.play.ZipPlayFixture
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.compete.api.DuelKey
import com.ivandsky.puzzlebox.compete.api.DuelRevealKey
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.match.ClaimResult
import com.ivandsky.puzzlebox.data.match.LegResult
import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.data.match.PostResult
import com.ivandsky.puzzlebox.data.match.ResultPostState
import com.ivandsky.puzzlebox.domain.DuelRowKind
import com.ivandsky.puzzlebox.domain.GetPuzzleDay
import com.ivandsky.puzzlebox.domain.LegSeen
import com.ivandsky.puzzlebox.domain.MatchState
import com.ivandsky.puzzlebox.domain.MatchView
import com.ivandsky.puzzlebox.domain.ObserveMatch
import com.ivandsky.puzzlebox.gameui.DragEvent
import com.ivandsky.puzzlebox.gameui.GridCell
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.games.zipui.ZipPlayController
import com.ivandsky.puzzlebox.history.api.HistoryKey
import com.ivandsky.puzzlebox.home.api.HomeKey
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.play.ActivePlay
import com.ivandsky.puzzlebox.play.api.MatchLegKey
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.profile.api.ProfileKey
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A whole duel over the real shell, the real compete screens and the real result poster, with a friend acting on
 * the fake server: I create a Daily duel, the friend claims it and plays, I play; the friend's time stays sealed
 * until my own result is posted; then the reveal plays once — intro, race, score, summary — and a reveal seen once,
 * even when the process died in the middle of it, opens at its summary from then on.
 */
@RunWith(RobolectricTestRunner::class)
// Plain Application: the merged manifest's PuzzleBoxApplication would start its own Koin.
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w411dp-h891dp")
class DuelFlowE2ETest {

    private val graph = E2eKoinGraph(E2E_TODAY, releaseFlags = FakeReleaseFlags(ReleaseFlag.Competitions))

    @Test
    fun aCreatedDuelReachesTheRevealAndThenTheSummary() = duel {
        val code = createDuel(ZIP)
        val match = checkNotNull(graph.matches.matches[code])
        assertEquals("a Daily duel over Zip alone has one puzzle, Zip's", listOf(ZIP), match.legs.map { it.ref.gameId })
        tap(CREATED_DONE)
        assertEquals("the new duel waits for a friend to accept: nothing to play yet", DuelRowKind.Unclaimed, row(code).kind)

        // My invite waits; the friend's claim turns it into the duel's puzzles.
        tapDuelRow(code)
        awaitTag(INVITE_CODE)
        assertFalse("nothing to play before a claim", shows(LEGS_TITLE))
        friendClaims(code)
        awaitTag(LEGS_TITLE)
        assertFalse("the invite is gone", shows(INVITE_CODE))
        friendPlays(code)

        playMyLeg(code)
        tap(RESULT_UP)
        awaitTag(SEE_REVEAL)
        assertEquals("the result led back to the duel", DuelKey(code.value), state.currentStack.last())

        // The ready reveal needs me: the Duels pill counts it and Play carries a dot while I am elsewhere.
        back()
        awaitTag(DUELS_COUNT)
        val ready = duels()
        assertEquals("the duel's row is a ready reveal", DuelRowKind.RevealReady, ready.rows.single().kind)
        assertEquals("the ready reveal is the one duel needing me", 1, ready.needYou)
        tapTab(HomeKey)
        assertTrue("Play carries the dot", shows(PLAY_BADGE))
        tapTab(PlayKey)

        // The row opens the reveal itself; it plays intro, race and score, then the summary.
        openDuelsTab()
        tapDuelRow(code, holdClock = true)
        advanceUntilShown(INTRO, never = listOf(SUMMARY))
        assertNotNull("opening the reveal records that it was seen", revealSeenAt(code))
        val intro = advanceUntilShown(CARD, never = listOf(SUMMARY))
        assertTrue("the race shows the score", shows(SCORE))
        assertFalse("the race replaced the intro", shows(INTRO))
        val race = advanceUntilShown(SUMMARY)
        val natural = intro + race
        listOf("reveal.summary.mine", "reveal.summary.theirs", "reveal.share", "reveal.rematch", WATCH_AGAIN)
            .forEach { assertTrue("the summary shows $it", shows(it)) }
        assertFalse("the stage is gone", shows(SKIP))

        // Watch again plays from the intro; a tap on the stage speeds it up for the rest of the run. The summary
        // scrolls, so the clock runs while Watch again is scrolled to.
        rule.mainClock.autoAdvance = true
        holdClockAndTap(WATCH_AGAIN)
        advanceUntilShown(INTRO)
        assertTrue("a new run offers the speed-up", shows(SPEED_UP))
        tapRevealStage()
        advanceFrame()
        assertFalse("the speed-up sticks", shows(SPEED_UP))
        val fast = advanceUntilShown(SUMMARY)
        assertTrue("a sped-up run ($fast ms) beats the natural one ($natural ms)", fast < natural)

        // Skip jumps to the summary within a frame.
        rule.mainClock.autoAdvance = true
        holdClockAndTap(WATCH_AGAIN)
        advanceUntilShown(INTRO)
        tap(SKIP)
        advanceFrame()
        assertTrue("Skip lands on the summary", shows(SUMMARY))
        rule.mainClock.autoAdvance = true
    }

    @Test
    fun theOpponentsTimeStaysSealedUntilMyOwnResultExists() = duel {
        val code = createDuel(ZIP)
        tap(CREATED_DONE)
        friendClaims(code)
        friendPlays(code)

        awaitIdling { view(code).theirs.firstOrNull() == LegSeen.Played }
        assertEquals("their leg reads played, its time sealed", listOf<LegSeen>(LegSeen.Played), view(code).theirs)
        assertEquals("the duel waits on my move", DuelRowKind.YourMove, row(code).kind)
        openDuel(code)
        assertFalse("no reveal before my result", shows(SEE_REVEAL))

        playMyLeg(code)
        awaitIdling { view(code).theirs.firstOrNull() is LegSeen.Revealed }
        val theirs = view(code).theirs.single() as LegSeen.Revealed
        assertEquals("my posted result unseals their time", FRIEND_TIME_MS, theirs.result.timeMs)
        awaitIdling { row(code).kind == DuelRowKind.RevealReady }
        tap(RESULT_UP)
        awaitTag(SEE_REVEAL)
    }

    @Test
    fun aProcessDeathMidRevealShowsTheRevealOnceThenTheSummary() = duel {
        val code = playToRevealReady()
        holdClockAndTap(SEE_REVEAL)
        advanceUntilShown(CARD, never = listOf(SUMMARY))
        assertNotNull("the reveal was recorded as seen when it started", revealSeenAt(code))

        relaunch(keepSavedState = true)
        advanceUntilShown(SUMMARY, never = STAGE)
        assertEquals("the restored reveal is the duel's", DuelRevealKey(code.value), state.currentStack.last())
        rule.mainClock.autoAdvance = true

        tap(REVEAL_BACK)
        awaitTag(SEE_REVEAL)
        holdClockAndTap(SEE_REVEAL)
        advanceUntilShown(SUMMARY, never = STAGE)
        rule.mainClock.autoAdvance = true
    }

    @Test
    fun aSeenRevealIsNeverShownAgain() = duel {
        val code = playToRevealReady()
        holdClockAndTap(SEE_REVEAL)
        advanceUntilShown(INTRO, never = listOf(SUMMARY))
        rule.mainClock.autoAdvance = true
        back()
        awaitTag(SUMMARY)

        // Left and opened again, it opens at the summary.
        tap(REVEAL_BACK)
        holdClockAndTap(SEE_REVEAL)
        advanceUntilShown(SUMMARY, never = STAGE)
        rule.mainClock.autoAdvance = true

        // The seen duel needs nothing more: no row, no count, no dot, even after the app restarts.
        tap(REVEAL_BACK)
        tap(DUEL_BACK)
        awaitTag(DUELS_LIST)
        assertTrue("the seen duel left the list", duels().rows.none { it.code == code })
        assertFalse("the Duels pill counts nothing", shows(DUELS_COUNT))
        relaunch()
        assertTrue("the seen duel stays off the list after a restart", duels().rows.none { it.code == code })
        tapTab(HomeKey)
        assertFalse("Play carries no dot", shows(PLAY_BADGE))
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

    /** Created, claimed and played by both, my result posted; left on the duel screen with its reveal ready. */
    private fun ShellRobot.playToRevealReady(): ShareCode {
        val code = createDuel(ZIP)
        tap(CREATED_DONE)
        friendClaims(code)
        friendPlays(code)
        playMyLeg(code)
        tap(RESULT_UP)
        awaitTag(SEE_REVEAL)
        return code
    }

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

    private fun ShellRobot.row(code: ShareCode) = duels().rows.single { it.code == code }

    private fun view(code: ShareCode): MatchView =
        when (val state = runBlocking { graph.koin.get<ObserveMatch>()(code).first() }) {
            is MatchState.Loaded -> state.view
            else -> error("duel ${code.value} is $state")
        }

    private fun revealSeenAt(code: ShareCode): Instant? =
        runBlocking { graph.matchLocal.observeAll().first() }.single { it.code == code }.revealSeenAt

    /** The Play tab is selected; Home's stack holds History. */
    private val onPlay = NavigationState(
        tab = PlayKey,
        stacks = listOf(listOf(HomeKey, HistoryKey()), listOf(PlayKey), listOf(ProfileKey)),
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
        const val CREATED_DONE = "duel.created.done"
        const val INVITE_CODE = "duel.invite.code"
        const val LEGS_TITLE = "duel.legs.title"
        const val SEE_REVEAL = "duel.legs.reveal"
        const val DUEL_BACK = "duel.back"
        const val DUELS_LIST = "duels.tab"
        const val DUELS_COUNT = "library.tab.duels.count"
        const val INTRO = "reveal.intro"
        const val CARD = "reveal.card"
        const val SCORE = "reveal.score"
        const val SKIP = "reveal.skip"
        const val SPEED_UP = "reveal.speedUp"
        const val SUMMARY = "reveal.summary"
        const val WATCH_AGAIN = "reveal.watchAgain"
        const val REVEAL_BACK = "reveal.back"

        /** The reveal's animated stage, which a seen reveal never shows. */
        val STAGE = listOf(INTRO, CARD, SKIP)
        val PLAY_BADGE = "${topLevelDestinations.single { it.key == PlayKey }.tag}.badge"
    }
}
