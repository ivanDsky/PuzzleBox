package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.domain.ranks.PlayerActions
import com.ivandsky.puzzlebox.domain.ranks.PlayerActions.Challenge
import com.ivandsky.puzzlebox.domain.ranks.PlayerActions.Watch
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.board.DailyBoardState
import com.ivandsky.puzzlebox.history.ranks.PlayerSheetViewModel.Challenge as ChallengeStatus
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The player sheet from plain state, with no view model behind it: what each challenge status and Watch gate draws and
 * takes, and what the hub does with the sheet it hosts.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class PlayerSheetTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val zip = PlayerSheetViewModel.Game(GameId("zip"), "Zip", PuzzleIcons.Play)
    private val tango = PlayerSheetViewModel.Game(GameId("tango"), "Tango", PuzzleIcons.Check)
    private val onZip = PlayerSheetViewModel.Target("maya", "Maya", Standing.Place(2), GameId("zip"), LocalDate.of(2026, 11, 13))
    private val onOverall = onZip.copy(game = null, day = null)
    private val hub = RanksUiState(RanksBoardSlice("Zip · today", RankOrder.TimeThenHints, DailyBoardState.Loading))

    private val challenged = mutableListOf<GameId>()
    private val events = mutableListOf<String>()

    private fun zipSheet(challenge: Challenge? = Challenge.Allowed, watch: Watch = Watch.Allowed, status: ChallengeStatus = ChallengeStatus.Idle) =
        PlayerSheetViewModel.State(onZip, challenge?.let { PlayerActions(it, watch) }, listOf(zip), status)

    private fun overallSheet(challenge: Challenge = Challenge.Allowed, status: ChallengeStatus = ChallengeStatus.Idle) =
        PlayerSheetViewModel.State(onOverall, PlayerActions(challenge, Watch.Unavailable), listOf(zip, tango), status)

    private fun show(state: PlayerSheetViewModel.State, onWatch: (() -> Unit)? = {}) {
        composeRule.setContent {
            PuzzleBoxTheme { PlayerSheet(state, onChallenge = { challenged += it }, onDismiss = { events += "dismiss" }, onWatch = onWatch) }
        }
    }

    private fun showInHub(sheet: PlayerSheetViewModel.State) {
        composeRule.setContent {
            PuzzleBoxTheme {
                RanksScreen(
                    hub,
                    onSignIn = {},
                    onSwipe = {},
                    onWatchPlayer = { events += "watch ${it.uid}" },
                    playerSheet = sheet,
                    onChallenge = { challenged += it },
                    onClosePlayer = { events += "close" },
                )
            }
        }
    }

    private fun challenge() = composeRule.onNodeWithContentDescription("Challenge")

    private fun caption(tile: String) = composeRule.onNodeWithTag("ranks.player.$tile.caption")

    private fun count(tag: String) = composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().size

    private fun swipeAway() {
        composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.Dismiss)).performSemanticsAction(SemanticsActions.Dismiss)
        composeRule.waitForIdle()
    }

    @Test
    fun `the sheet names the player`() {
        show(zipSheet())

        composeRule.onNodeWithTag("ranks.player.name").assertTextEquals("Maya")
    }

    @Test
    fun `the sheet says the player's place`() {
        show(zipSheet())

        composeRule.onNodeWithTag("ranks.player.place").assertTextEquals("#2")
    }

    @Test
    fun `a Challenge tap on a game's board sends that game`() {
        show(zipSheet())

        challenge().performClick()

        assertEquals(listOf(GameId("zip")), challenged)
    }

    @Test
    fun `Overall's Challenge asks to pick a game`() {
        show(overallSheet())

        caption("challenge").assertTextEquals("Pick a game")
        challenge().assertIsNotEnabled()
    }

    @Test
    fun `a game picked on Overall sends that game`() {
        show(overallSheet())

        composeRule.onNodeWithContentDescription("Challenge to Tango").performClick()

        assertEquals(listOf(GameId("tango")), challenged)
    }

    @Test
    fun `a private Challenge is locked`() {
        show(zipSheet(challenge = Challenge.Private))

        challenge().assertIsNotEnabled()
    }

    @Test
    fun `a private player has no games to pick from on Overall`() {
        show(overallSheet(challenge = Challenge.Private))

        assertEquals(0, count("ranks.player.games"))
    }

    @Test
    fun `a failed Challenge says why`() {
        show(zipSheet(status = ChallengeStatus.Failed("You're offline")))

        caption("challenge").assertTextEquals("You're offline")
    }

    @Test
    fun `a failed Challenge can be sent again`() {
        show(zipSheet(status = ChallengeStatus.Failed("You're offline")))

        challenge().performClick()

        assertEquals(listOf(GameId("zip")), challenged)
    }

    @Test
    fun `a Challenge on its way says Sending`() {
        show(zipSheet(status = ChallengeStatus.Sending))

        caption("challenge").assertTextEquals("Sending…")
    }

    @Test
    fun `a Challenge on its way takes no second tap`() {
        show(zipSheet(status = ChallengeStatus.Sending))

        challenge().assertIsNotEnabled()
    }

    @Test
    fun `a Challenge on its way locks Overall's games`() {
        show(overallSheet(status = ChallengeStatus.Sending))

        composeRule.onNodeWithTag("ranks.player.game.tango").assertIsNotEnabled()
    }

    @Test
    fun `a sent Challenge takes no second tap`() {
        show(zipSheet(status = ChallengeStatus.Sent("Maya")))

        challenge().assertIsNotEnabled()
    }

    @Test
    fun `a sent Challenge drops Overall's games`() {
        show(overallSheet(status = ChallengeStatus.Sent("Maya")))

        assertEquals(0, count("ranks.player.games"))
    }

    @Test
    fun `a player with nothing to watch has no Watch tile`() {
        show(zipSheet(watch = Watch.Unavailable))

        assertEquals(0, count("ranks.player.watch"))
    }

    @Test
    fun `an allowed Watch with no host to open it stays disabled`() {
        show(zipSheet(), onWatch = null)

        composeRule.onNodeWithContentDescription("Watch").assertIsNotEnabled()
    }

    @Test
    fun `Challenge is locked until the player's tier is read`() {
        show(zipSheet(challenge = null))

        challenge().assertIsNotEnabled()
    }

    @Test
    fun `Watch is locked until the player's tier is read`() {
        show(zipSheet(challenge = null))

        composeRule.onNodeWithContentDescription("Watch").assertIsNotEnabled()
    }

    @Test
    fun `swiping the sheet away dismisses it`() {
        show(zipSheet())

        swipeAway()

        assertEquals(listOf("dismiss"), events)
    }

    @Test
    fun `the hub draws the sheet its state has open`() {
        showInHub(zipSheet())

        composeRule.onNodeWithTag("ranks.player.name").assertTextEquals("Maya")
    }

    @Test
    fun `a Challenge tap in the hub's sheet reports the game`() {
        showInHub(zipSheet())

        challenge().performClick()

        assertEquals(listOf(GameId("zip")), challenged)
    }

    @Test
    fun `a Watch tap in the hub closes the sheet, then reports its player`() {
        showInHub(zipSheet())

        composeRule.onNodeWithContentDescription("Watch").assertIsEnabled().performClick()

        assertEquals(listOf("close", "watch maya"), events)
    }

    @Test
    fun `swiping the hub's sheet away closes it`() {
        showInHub(zipSheet())

        swipeAway()

        assertEquals(listOf("close"), events)
    }
}
