package com.ivandsky.puzzlebox.compete.tab

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.ivandsky.puzzlebox.auth.GoogleIdToken
import com.ivandsky.puzzlebox.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.compete.api.DuelRevealKey
import com.ivandsky.puzzlebox.compete.di.competeModule
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.LegResult
import com.ivandsky.puzzlebox.data.match.MatchDraft
import com.ivandsky.puzzlebox.data.match.MatchLeg
import com.ivandsky.puzzlebox.data.match.MatchResultSync
import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.data.match.ResultPostState
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.ObserveDuel
import com.ivandsky.puzzlebox.domain.ObserveMatch
import com.ivandsky.puzzlebox.domain.ObserveMyDuels
import com.ivandsky.puzzlebox.domain.UpdateMatchSeat
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.api.MatchLegKey
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeMatchLocalRepository
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
                    single { ObserveMyDuels(server, outbox, local, identity) { now } }
                    single { UpdateMatchSeat(server, local, identity) }
                    single<IdentityService> { identity }
                    single { ObserveDuel(observeMatch, FakeCompletionRepository(), FakeSavedPlayRepository()) }
                    single { MatchResultSync { } }
                    single<GoogleIdTokenProvider> {
                        object : GoogleIdTokenProvider {
                            override suspend fun idToken(activityContext: Context): GoogleIdToken = GoogleIdToken.Available("token")
                        }
                    }
                    single { registry }
                    single { GamePlayRegistry(emptyList()) }
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
        // The footer sits under the puzzle list in the pane's own scroll, as it does on the full screen.
        rule.onNode(inPane("duel.legs.reveal")).performScrollTo().assertIsDisplayed()
        assertTrue("a row tap only swaps the pane", pushed().isEmpty())

        rule.onNode(inPane("duel.legs.reveal")).performClick()
        rule.waitForIdle()

        assertEquals(DuelRevealKey(sealed.value), pushed().single())
    }
}
