package com.ivandsky.puzzlebox.app.e2e

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.app.play.ZipPlayFixture
import com.ivandsky.puzzlebox.data.match.MatchResultPoster
import com.ivandsky.puzzlebox.data.match.ResultPostState
import com.ivandsky.puzzlebox.data.match.SelfMatchRepository
import com.ivandsky.puzzlebox.domain.DuelRowKind
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.games.zipui.ZipPlayController
import com.ivandsky.puzzlebox.gameui.DragEvent
import com.ivandsky.puzzlebox.gameui.GridCell
import com.ivandsky.puzzlebox.history.api.HistoryKey
import com.ivandsky.puzzlebox.history.api.HistoryRootKey
import com.ivandsky.puzzlebox.home.api.HomeKey
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.play.ActivePlay
import com.ivandsky.puzzlebox.play.api.CommunityKey
import com.ivandsky.puzzlebox.play.api.MatchLegKey
import com.ivandsky.puzzlebox.play.api.PlayKey
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
 * A whole duel against Past you, signed out, over the real shell, the real compete screens, the real use cases and the
 * on-device self store (Room): Duels tab, the New duel sheet's Past you choice, Create, every leg played with its
 * time-left timer showing, Results ready, the reveal with its improvement line, and the duel as a finished row in
 * My duels. Nothing reaches the friend-duel server fake.
 */
@RunWith(RobolectricTestRunner::class)
// DrainTestApplication (plain Application + looper drain): the merged manifest's PuzzleBoxApplication would start its own Koin.
@Config(sdk = [34], application = DrainTestApplication::class, qualifiers = "w411dp-h891dp")
class SelfDuelE2ETest {

    private val graph = shellGraph()

    @Test
    fun aSignedOutPlayerDuelsPastYouFromTheSheetToAFinishedRow() = shellRobot(onPlay, graph = graph) {
        graph.seedSolvedZips(E2E_TODAY)
        graph.koin.get<MatchResultPoster>().start()
        openDuelsTab()
        assertTrue("the Challenge card offers dueling yourself", rule.onAllNodesWithText(SIGNED_OUT_LINE).fetchSemanticsNodes().isNotEmpty())

        tap("duels.challenge")
        awaitTag("duel.new.sheet")
        tap("duel.opponent.self")
        tap("duel.preset.quick")
        tap("duel.create")
        awaitTag("duel.legs.title")
        val code = selfDuelCodes().single()
        assertFalse("a duel against Past you has no code to share", shows("duel.invite.code"))

        repeat(SOLVED_ZIPS) { leg -> playLeg(code, leg) }
        awaitTag("duel.legs.reveal")
        awaitIdling { row(code).kind == DuelRowKind.RevealReady }

        holdClockAndTap("duel.legs.reveal")
        advanceUntilShown("reveal.intro", never = listOf("reveal.summary"))
        rule.mainClock.autoAdvance = true
        back()
        awaitTag("reveal.summary")
        awaitTag("reveal.summary.improvement")
        assertFalse("a self reveal has no Share", shows("reveal.share"))
        assertTrue("it offers the Rematch", shows("reveal.rematch"))

        tap("reveal.back")
        tap("duel.back")
        awaitTag("duels.tab")
        val kind = row(code).kind
        assertTrue("the seen duel is a finished row, not $kind", kind is DuelRowKind.Finished)
        assertTrue("it shows in My duels", shows("duel.row.${code.value}"))
        assertTrue("the friend-duel server never held it", graph.matches.matches.isEmpty())
    }

    /** Opens leg [index] from the duel page, checks its time-left timer, solves it and posts it. */
    private fun ShellRobot.playLeg(code: ShareCode, index: Int) {
        val leg = MatchLegKey(code.value, index, ZipDefinition.id.value)
        openDuelPuzzle(leg)
        awaitTag("play.timer")
        rule.waitUntil(TIMEOUT_MS) { rule.onAllNodes(hasText(" left", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        solveDuelPuzzle(leg, ready = { it.zip.session != null }) { play ->
            ZipPlayFixture.solution.forEach { play.zip.onDragEvent(DragEvent.Extend(GridCell(it.idx / SIZE, it.idx % SIZE))) }
        }
        awaitIdling {
            runBlocking { graph.outbox.observe(code).first() }.count { it.state == ResultPostState.Posted } == index + 1
        }
        tap("result.up")
    }

    private val ActivePlay.zip: ZipPlayController get() = controller as ZipPlayController

    private fun ShellRobot.row(code: ShareCode) = duels().rows.single { it.code == code }

    private fun ShellRobot.selfDuelCodes(): List<ShareCode> =
        runBlocking { graph.koin.get<SelfMatchRepository>().observeAll().first() }.map { it.code }

    /** The Play tab is selected; Home's stack holds History. */
    private val onPlay = NavigationState(
        tab = PlayKey,
        stacks = listOf(listOf(HomeKey, HistoryKey()), listOf(PlayKey), listOf(CommunityKey), listOf(HistoryRootKey)),
    )

    private companion object {
        const val SIZE = 5
        const val TIMEOUT_MS = 5_000L
        const val SIGNED_OUT_LINE = "Sign in to duel friends, or duel Past you now"
    }
}
