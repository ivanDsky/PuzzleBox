package com.ivandsky.puzzlebox.compete.tab

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.ivandsky.puzzlebox.auth.GoogleIdToken
import com.ivandsky.puzzlebox.testing.repository.FakePublishedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeReceivedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.domain.LoadSharedPuzzle
import com.ivandsky.puzzlebox.domain.LoadLegPuzzle
import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.common.dispatcher.DefaultDispatcherProvider
import com.ivandsky.puzzlebox.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.compete.api.DuelRevealKey
import com.ivandsky.puzzlebox.compete.di.competeModule
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.LegResult
import com.ivandsky.puzzlebox.data.match.MatchDraft
import com.ivandsky.puzzlebox.data.match.MatchLeg
import com.ivandsky.puzzlebox.data.match.MatchResultSync
import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.data.match.ResultPostState
import com.ivandsky.puzzlebox.designsystem.MaxContentWidth
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.data.repository.UserPreferencesRepository
import com.ivandsky.puzzlebox.domain.DeleteSelfDuel
import com.ivandsky.puzzlebox.domain.ObserveDuel
import com.ivandsky.puzzlebox.domain.ObserveMatch
import com.ivandsky.puzzlebox.domain.lineup.ObserveLegTitles
import com.ivandsky.puzzlebox.domain.ObserveMyDuels
import com.ivandsky.puzzlebox.domain.UpdateMatchSeat
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.api.MatchLegKey
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeMatchLocalRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSelfMatchRepository
import com.ivandsky.puzzlebox.testing.repository.FakeUserPreferencesRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchResultOutbox
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The real Duels tab beside the real pane on the app's compete Koin module, in the tablet layout. Sam's duel needs Bob's
 * move; Cat's is finished on both sides and ready to reveal. One [DuelsTabViewModel] drives both: a row tap swaps the
 * pane, and the pane holds the full duel screen's controls, which push their screens over both panes.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-land-mdpi")
class DuelsTabPaneTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val now = Instant.now()
    private val date = LocalDate.of(2026, 11, 9)
    private val mine = ShareCode.parse("K7Q2MX")!!
    private val sealed = ShareCode.parse("B4RT9P")!!
    private val bob = Player("bob", "Bob", null)
    private val sam = Player("sam", "Sam", null)
    private val cat = Player("cat", "Cat", null)
    private val navigator = Navigator(NavigationState(PlayKey, listOf(listOf(PlayKey))))

    private val server = FakeMatchRepository().apply { this.now = this@DuelsTabPaneTest.now }
    private val local = FakeMatchLocalRepository()
    private val outbox = FakeMatchResultOutbox()
    private val identity = FakeIdentityService()
    private val registry = TestGames.registry(listOf(TestGames.alpha, TestGames.beta))
    private val received = FakeReceivedPuzzleRepository()
    private val loadShared = LoadSharedPuzzle(
        registry = registry,
        shared = FakeSharedPuzzleRepository(),
        received = received,
        budget = { Budget.within(1_000, System::nanoTime) },
        clock = { now },
    )

    private fun viewAs(player: Player) {
        identity.currentIdentity = Identity(player.uid, player.name, null)
        server.signedInUid = player.uid
    }

    /** [challenger] opens [code] and Bob claims it; with [bothPlayed] each posts both legs, so the reveal is ready. */
    private fun duel(code: ShareCode, challenger: Player, bothPlayed: Boolean) = runBlocking {
        viewAs(challenger)
        val legs = listOf(
            MatchLeg(LegRef.Daily(TestGames.alpha, date, 1), null),
            MatchLeg(LegRef.Daily(TestGames.beta, date, 2), null),
        )
        server.create(MatchDraft(code, 24, challenger, null, legs, includeFriends = false, rematchOf = null))
        viewAs(bob)
        server.claim(code, bob)
        if (bothPlayed) {
            for (player in listOf(challenger, bob)) {
                viewAs(player)
                repeat(2) { leg ->
                    val result = LegResult(player.uid, leg, 90_000, 0, completedAtMs = now.toEpochMilli(), gaveUp = false)
                    server.post(code, result)
                    if (player == bob) {
                        outbox.enqueue(code, result)
                        outbox.update(code, leg, bob.uid, ResultPostState.Posted, attempts = 0)
                    }
                }
            }
        }
        viewAs(bob)
    }

    @Before
    fun startKoinWithTheRealCompeteModule() {
        duel(mine, sam, bothPlayed = false)
        duel(sealed, cat, bothPlayed = true)
        val observeMatch = ObserveMatch(server, outbox, local, identity, registry) { now }
        startKoin {
            modules(
                competeModule,
                module {
                    single { ObserveMyDuels(server, FakeSelfMatchRepository(), outbox, local, identity) { now } }
                    single { UpdateMatchSeat(server, local, identity) }
                    single<IdentityService> { identity }
                    single<ReleaseFlags> { FakeReleaseFlags() }
                    single { ObserveDuel(observeMatch, FakeCompletionRepository(), FakeSavedPlayRepository()) }
                    single { DeleteSelfDuel(FakeSelfMatchRepository(), outbox, FakeSavedPlayRepository(), local) }
                    single<UserPreferencesRepository> { FakeUserPreferencesRepository() }
                    single { MatchResultSync { } }
                    single<GoogleIdTokenProvider> {
                        object : GoogleIdTokenProvider {
                            override suspend fun idToken(activityContext: Context): GoogleIdToken = GoogleIdToken.Available("token")
                        }
                    }
                    single { registry }
                    single { ObserveLegTitles(received, FakePublishedPuzzleRepository(), FakeSharedPuzzleRepository()) }
                    single { GamePlayRegistry(emptyList()) }
                    single<DispatcherProvider> { DefaultDispatcherProvider() }
                    single { LoadLegPuzzle(daily = FakeDailyRepository(), received = received, loadShared = loadShared) }
                },
            )
        }
    }

    @After
    fun stopKoinAgain() = stopKoin()

    private fun show() {
        val pane = duelsPane(navigator, PlayLayout.TwoPane)
        rule.setContent {
            PuzzleBoxTheme {
                Row(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f)) { DuelsTab(onOpenDuel = {}, onOpenReveal = {}, inPane = true) }
                    Box(Modifier.weight(2f)) { pane(Modifier.fillMaxSize()) }
                }
            }
        }
        rule.waitForIdle()
    }

    /** The pane alone, [width] wide and the window's height, for a pane too narrow for two columns. */
    private fun showAlone(width: Dp) {
        val pane = duelsPane(navigator, PlayLayout.TwoPane)
        rule.setContent {
            PuzzleBoxTheme { Box(Modifier.width(width).fillMaxHeight()) { pane(Modifier.fillMaxSize()) } }
        }
        rule.waitForIdle()
    }

    private fun bounds(tag: String) = rule.onNodeWithTag(tag).getUnclippedBoundsInRoot()

    private fun inPane(tag: String) = hasTestTag(tag) and hasAnyAncestor(hasTestTag("duels.pane"))

    private fun pushed() = navigator.state.currentStack.drop(1)

    @Test
    fun `the pane opens on the duel that needs Bob with its puzzles and Start, and the row is highlighted`() {
        show()

        rule.onNodeWithTag("duel.row.${mine.value}").assertIsSelected()
        rule.onNodeWithTag("duel.row.${sealed.value}").assertIsNotSelected()
        rule.onNode(inPane("duel.legs.title")).assertIsDisplayed()
        rule.onNode(inPane("duel.legs.card.0")).assertIsDisplayed()
        rule.onNode(inPane("duel.legs.card.1")).assertIsDisplayed()
        rule.onNode(inPane("duel.legs.action.0")).assertIsDisplayed()
        rule.onAllNodesWithTag("duel.legs.reveal").assertCountEquals(0)
    }

    @Test
    fun `Start in the pane pushes the puzzle's screen over both panes`() {
        show()

        rule.onNode(inPane("duel.legs.action.0")).performClick()
        rule.waitForIdle()

        assertEquals(MatchLegKey(mine.value, 0, TestGames.alpha.value), pushed().single())
    }

    @Test
    fun `tapping the other row swaps the pane to that duel without pushing, and See results opens its reveal`() {
        show()

        rule.onNodeWithTag("duel.row.${sealed.value}").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("duel.row.${sealed.value}").assertIsSelected()
        rule.onNodeWithTag("duel.row.${mine.value}").assertIsNotSelected()
        rule.onNode(inPane("duel.legs.card.1")).assertIsDisplayed()
        // The footer is pinned under the puzzle list, outside its scroll.
        rule.onNode(inPane("duel.legs.reveal")).assertIsDisplayed()
        assertTrue("a row tap only swaps the pane", pushed().isEmpty())

        rule.onNode(inPane("duel.legs.reveal")).performClick()
        rule.waitForIdle()

        assertEquals(DuelRevealKey(sealed.value), pushed().single())
    }

    @Test
    fun `a picked duel fills the pane in two panes with a divider, Start under the card`() {
        show()

        val pane = bounds("duels.pane")
        val grid = bounds("duel.legs.grid")
        val left = bounds("duel.legs.grid.left")
        val right = bounds("duel.legs.grid.right")
        assertEquals("fills across", pane.width.value, grid.width.value, 1f)
        assertEquals("fills down", pane.height.value, grid.height.value, 1f)
        assertTrue("left of the right pane", left.right <= right.left)
        assertEquals("Start ends the detail, not pinned to the pane", bounds("duel.legs.detail").bottom.value, bounds("duel.legs.action.0").bottom.value, 1f)
        assertTrue("room below", bounds("duel.legs.action.0").bottom.value < pane.bottom.value - 8f)
    }

    @Test
    fun `a wide pane fills its width too, and Start takes the right pane's width less its padding`() {
        showAlone(1200.dp)

        val pane = bounds("duels.pane")
        val grid = bounds("duel.legs.grid")
        assertEquals("fills across", pane.width.value, grid.width.value, 1f)
        assertEquals("Start fills the right pane", bounds("duel.legs.grid.right").width.value - 32f, bounds("duel.legs.action.0").width.value, 1.5f)
    }

    @Test
    fun `a pane too narrow for two columns keeps one, capped at the content width and centred, with Start on each card`() {
        showAlone(740.dp)

        rule.onAllNodesWithTag("duel.legs.grid").assertCountEquals(0)
        rule.onAllNodesWithTag("duel.legs.detail").assertCountEquals(0)
        val pane = bounds("duels.pane")
        val column = bounds("duel.legs.column")
        assertTrue("capped: ${column.width}", column.width <= MaxContentWidth + 1.dp)
        assertEquals("centred across", (pane.left + pane.right).value / 2f, (column.left + column.right).value / 2f, 1f)
        rule.onNode(inPane("duel.legs.action.0")).assertIsDisplayed()
    }
}
