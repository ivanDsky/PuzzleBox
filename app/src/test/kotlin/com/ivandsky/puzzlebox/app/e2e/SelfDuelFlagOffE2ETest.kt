package com.ivandsky.puzzlebox.app.e2e

import androidx.compose.ui.test.onAllNodesWithText
import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.app.play.ZipPlayFixture
import com.ivandsky.puzzlebox.data.match.MatchResultPoster
import com.ivandsky.puzzlebox.data.match.ResultPostState
import com.ivandsky.puzzlebox.domain.CreateOutcome
import com.ivandsky.puzzlebox.domain.CreateSelfDuel
import com.ivandsky.puzzlebox.domain.DuelRowKind
import com.ivandsky.puzzlebox.domain.SelfDuelSettings
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.games.zipui.ZipPlayController
import com.ivandsky.puzzlebox.gameui.DragEvent
import com.ivandsky.puzzlebox.gameui.GridCell
import com.ivandsky.puzzlebox.history.api.HistoryKey
import com.ivandsky.puzzlebox.history.api.HistoryRootKey
import com.ivandsky.puzzlebox.home.api.HomeKey
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.play.api.CommunityKey
import com.ivandsky.puzzlebox.play.api.MatchLegKey
import com.ivandsky.puzzlebox.play.api.PlayKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * VirtualDuel off, as in release: the entries to a self duel are gone — the New duel sheet's Play against row, the
 * Duels tab's "duel yourself" line, Settings' time-left row — while a self duel stored before the flag turned off
 * stays in My duels and plays on (the flag gates entries only).
 */
@RunWith(RobolectricTestRunner::class)
// DrainTestApplication (plain Application + looper drain): the merged manifest's PuzzleBoxApplication would start its own Koin.
@Config(sdk = [34], application = DrainTestApplication::class, qualifiers = "w411dp-h891dp")
class SelfDuelFlagOffE2ETest {

    private val graph = E2eKoinGraph(E2E_TODAY)

    @Test
    fun withTheFlagOffNoEntryToASelfDuelShows() = shellRobot(onPlay, graph = graph) {
        openDuelsTab()
        assertTrue("the Challenge card keeps its old line", rule.onAllNodesWithText("Sign in to duel friends").fetchSemanticsNodes().isNotEmpty())
        assertTrue("without the self line", rule.onAllNodesWithText("Sign in to duel friends, or duel yourself now").fetchSemanticsNodes().isEmpty())

        tap("duels.challenge")
        awaitTag("duel.new.sheet")
        assertFalse("the sheet offers no Play against choice", shows("duel.opponent.self"))
        assertFalse("nor the friend choice beside it", shows("duel.opponent.friend"))

        back()
        tapTab(HomeKey)
        tapAvatar()
        tapGear()
        assertRoute("route.settings")
        assertFalse("Settings has no Play section", shows("settings.play"))
        assertFalse("nor the time-left row", shows("settings.play.timeleft"))
    }

    @Test
    fun aSelfDuelStoredBeforeTheFlagTurnedOffStillListsAndPlays() = shellRobot(onPlay, graph = graph) {
        graph.seedSolvedZips(E2E_TODAY)
        graph.koin.get<MatchResultPoster>().start()
        val created = runBlocking {
            graph.koin.get<CreateSelfDuel>()(SelfDuelSettings(rounds = 1, games = setOf(ZipDefinition.id), includeFriends = false))
        }
        val code = (created as CreateOutcome.Created).code

        openDuelsTab()
        tapDuelRow(code)
        awaitTag("duel.legs.title")
        assertTrue("it is still a duel waiting on my move", duels().rows.single { it.code == code }.kind == DuelRowKind.YourMove)

        val leg = MatchLegKey(code.value, 0, ZipDefinition.id.value)
        openDuelPuzzle(leg)
        solveDuelPuzzle(leg, ready = { (it.controller as ZipPlayController).session != null }) { play ->
            ZipPlayFixture.solution.forEach { (play.controller as ZipPlayController).onDragEvent(DragEvent.Extend(GridCell(it.idx / SIZE, it.idx % SIZE))) }
        }
        awaitIdling { runBlocking { graph.outbox.observe(code).first() }.any { it.state == ResultPostState.Posted } }
        tap("result.up")
        awaitTag("duel.legs.reveal")
    }

    /** The Play tab is selected; Home's stack holds History. */
    private val onPlay = NavigationState(
        tab = PlayKey,
        stacks = listOf(listOf(HomeKey, HistoryKey()), listOf(PlayKey), listOf(CommunityKey), listOf(HistoryRootKey)),
    )

    private companion object {
        const val SIZE = 5
    }
}
