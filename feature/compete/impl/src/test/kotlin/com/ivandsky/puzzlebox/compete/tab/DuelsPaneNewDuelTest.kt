package com.ivandsky.puzzlebox.compete.tab

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.auth.GoogleIdToken
import com.ivandsky.puzzlebox.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.common.dispatcher.DefaultDispatcherProvider
import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.compete.create.DuelChallenge
import com.ivandsky.puzzlebox.compete.create.NewDuelHarness
import com.ivandsky.puzzlebox.compete.create.Opponent
import com.ivandsky.puzzlebox.compete.create.newDuelModule
import com.ivandsky.puzzlebox.compete.di.competeModule
import com.ivandsky.puzzlebox.compete.domain.ObserveMyDuels
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.MatchDraft
import com.ivandsky.puzzlebox.data.match.MatchLeg
import com.ivandsky.puzzlebox.data.match.MatchResultSync
import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.data.repository.UserPreferencesRepository
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.DeleteSelfDuel
import com.ivandsky.puzzlebox.domain.LoadLegPuzzle
import com.ivandsky.puzzlebox.domain.LoadSharedPuzzle
import com.ivandsky.puzzlebox.domain.ObserveDuel
import com.ivandsky.puzzlebox.domain.ObserveMatch
import com.ivandsky.puzzlebox.domain.UpdateMatchSeat
import com.ivandsky.puzzlebox.domain.lineup.ObserveLegTitles
import com.ivandsky.puzzlebox.domain.lineup.SaveDuelPuzzles
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchLocalRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchResultOutbox
import com.ivandsky.puzzlebox.testing.repository.FakePublishedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeReceivedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeUserPreferencesRepository
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Duels list beside the real pane with SheetScreens on, on the real [DuelsTabViewModel] and a [NewDuelHarness]
 * New duel view model: New duel is the pane's form (from the card or the empty button), never a sheet, and every way out
 * leaves the pane showing what it showed before.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-land-mdpi")
class DuelsPaneNewDuelTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val now = Instant.now()
    private val harness = NewDuelHarness()
    private val mine = ShareCode.parse("K7Q2MX")!!
    private val navigator = Navigator(NavigationState(PlayKey, listOf(listOf(PlayKey))))
    private var taken = 0

    @After
    fun stopKoinAgain() = stopKoin()

    /** Ann's own duel with Sam, so the list has a row the pane selects. */
    private fun withADuel() = runBlocking {
        harness.server.now = now
        val legs = listOf(MatchLeg(LegRef.Daily(TestGames.alpha, LocalDate.of(2026, 11, 9), 1), null))
        harness.server.create(MatchDraft(mine, 24, Player("ann", "Ann", null), null, legs, includeFriends = false, rematchOf = null))
    }

    private fun show(challenge: DuelChallenge? = null) {
        val registry = TestGames.registry(listOf(TestGames.alpha, TestGames.beta))
        val outbox = FakeMatchResultOutbox()
        val local = FakeMatchLocalRepository()
        val received = FakeReceivedPuzzleRepository()
        val loadShared = LoadSharedPuzzle(registry, FakeSharedPuzzleRepository(), received, { Budget.within(1_000, System::nanoTime) }) { now }
        val observeMatch = ObserveMatch(harness.server, outbox, local, harness.identity, registry) { now }
        startKoin {
            modules(
                competeModule,
                newDuelModule(),
                module {
                    single { ObserveMyDuels(harness.server, harness.self, outbox, local, harness.identity) { now } }
                    single { UpdateMatchSeat(harness.server, local, harness.identity) }
                    single<IdentityService> { harness.identity }
                    single<ReleaseFlags> { FakeReleaseFlags(ReleaseFlag.SheetScreens) }
                    viewModel { harness.viewModel }
                    single { ObserveDuel(observeMatch, FakeCompletionRepository(), FakeSavedPlayRepository()) }
                    single { DeleteSelfDuel(harness.self, outbox, FakeSavedPlayRepository(), local) }
                    single<UserPreferencesRepository> { FakeUserPreferencesRepository() }
                    single { MatchResultSync { } }
                    single<GoogleIdTokenProvider> {
                        object : GoogleIdTokenProvider {
                            override suspend fun idToken(activityContext: Context): GoogleIdToken = GoogleIdToken.Available("token")
                        }
                    }
                    single { registry }
                    single { ObserveLegTitles(received, FakePublishedPuzzleRepository(), FakeSharedPuzzleRepository()) }
                    single { SaveDuelPuzzles(loadShared::invoke) }
                    single { GamePlayRegistry(emptyList()) }
                    single<DispatcherProvider> { DefaultDispatcherProvider() }
                    single { LoadLegPuzzle(daily = FakeDailyRepository(), received = received, loadShared = loadShared, published = FakePublishedPuzzleRepository()) }
                },
            )
        }
        val pane = duelsPane(navigator, PlayLayout.TwoPane)
        rule.setContent {
            PuzzleBoxTheme {
                Row(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f)) { DuelsTab(onOpenDuel = {}, onOpenReveal = {}, inPane = true, challenge = challenge, onChallengeTaken = { taken++ }) }
                    Box(Modifier.weight(2f)) { pane(Modifier.fillMaxSize()) }
                }
            }
        }
        rule.waitForIdle()
    }

    private fun inPane(tag: String) = hasTestTag(tag) and hasAnyAncestor(hasTestTag("duels.pane"))

    private fun assertForm() {
        rule.onNode(inPane("duel.new.screen")).assertIsDisplayed()
        rule.onNode(inPane("duel.new.close")).assertIsDisplayed()
        rule.onAllNodesWithTag("duel.new.sheet").assertCountEquals(0)
        rule.onAllNodesWithTag("duel.new.back").assertCountEquals(0)
    }

    private fun assertNoForm() = rule.onAllNodesWithTag("duel.new.screen").assertCountEquals(0)

    @Test
    fun `the empty pane's New duel button shows the form in the pane, not a sheet`() {
        show()

        rule.onNodeWithTag("duels.pane.newDuel").performClick()
        rule.waitForIdle()

        assertForm()
        rule.onAllNodesWithTag("duels.pane.empty").assertCountEquals(0)
    }

    @Test
    fun `the list's New duel card shows the form in the pane`() {
        show()

        rule.onNodeWithTag("duels.challenge").performClick()
        rule.waitForIdle()

        assertForm()
    }

    @Test
    fun `Close brings back the empty pane and the next opening starts from Quick`() {
        show()
        rule.onNodeWithTag("duels.pane.newDuel").performClick()
        rule.waitForIdle()
        rule.runOnIdle { harness.viewModel.setDeadline(48) }
        rule.waitForIdle()
        assertEquals(48, harness.viewModel.state.value.draft.deadlineHours)

        rule.onNodeWithTag("duel.new.close").performClick()
        rule.waitForIdle()

        assertNoForm()
        rule.onNodeWithTag("duels.pane.empty").assertIsDisplayed()

        rule.onNodeWithTag("duels.pane.newDuel").performClick()
        rule.waitForIdle()

        assertEquals(24, harness.viewModel.state.value.draft.deadlineHours)
    }

    @Test
    fun `a duel in the list stays unmarked while the form is up, and tapping it ends the form`() {
        withADuel()
        show()
        rule.onNodeWithTag("duel.row.${mine.value}").assertIsSelected()
        rule.onNode(inPane("duels.pane.duel")).assertIsDisplayed()

        rule.onNodeWithTag("duels.challenge").performClick()
        rule.waitForIdle()

        assertForm()
        rule.onNodeWithTag("duel.row.${mine.value}").assert(!isSelected())

        rule.onNodeWithTag("duel.row.${mine.value}").performClick()
        rule.waitForIdle()

        assertNoForm()
        rule.onNodeWithTag("duel.row.${mine.value}").assertIsSelected()
        rule.onNode(inPane("duels.pane.duel")).assertIsDisplayed()
    }

    @Test
    fun `Close shows the duel that was selected before`() {
        withADuel()
        show()
        rule.onNodeWithTag("duels.challenge").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("duel.new.close").performClick()
        rule.waitForIdle()

        assertNoForm()
        rule.onNodeWithTag("duel.row.${mine.value}").assertIsSelected()
        rule.onNode(inPane("duels.pane.duel")).assertIsDisplayed()
    }

    @Test
    fun `a created friend duel shows Duel created over the window and Done closes the form`() {
        show()
        rule.onNodeWithTag("duels.pane.newDuel").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("duel.create").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("duel.created.sheet").fetchSemanticsNodes().isNotEmpty() }

        rule.onNodeWithTag("duel.created.done").performClick()
        rule.waitForIdle()

        rule.onAllNodesWithTag("duel.created.sheet").assertCountEquals(0)
        assertNoForm()
    }

    @Test
    fun `a duel against Past you is selected in the pane once created`() {
        harness.solveSome()
        show()
        rule.onNodeWithTag("duels.pane.newDuel").performClick()
        rule.waitForIdle()
        rule.runOnIdle { harness.viewModel.chooseOpponent(Opponent.PastYou) }
        rule.waitForIdle()

        rule.onNodeWithTag("duel.create").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("duel.new.screen").fetchSemanticsNodes().isEmpty() }
        rule.waitForIdle()

        val code = runBlocking { harness.self.observeAll().first() }.single().code
        rule.onNodeWithTag("duel.row.${code.value}").assertIsSelected()
        rule.onNode(inPane("duels.pane.duel")).assertIsDisplayed()
    }

    @Test
    fun `a challenge opens the form with its friend and is taken once`() {
        show(DuelChallenge(friendUid = "sam", friendName = "Sam", game = GameId("alpha")))

        assertForm()
        rule.onNodeWithTag("duel.invitee").assertTextContains("Sam", substring = true)
        assertEquals(1, taken)
    }
}
