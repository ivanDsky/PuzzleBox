package com.ivandsky.puzzlebox.compete.duel

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.lineup.LegName
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.testing.game.TestGames
import java.time.Clock
import java.time.ZoneOffset
import java.util.Locale
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A friend's puzzle shows its title and, when it cannot be started, why (A9, A16). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DuelLegUnavailableTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val first = checkNotNull(ShareCode.parse("K7Q2MX"))
    private val second = checkNotNull(ShareCode.parse("B8C3DF"))
    private val refs = listOf(LegRef.Custom(TestGames.alpha, first), LegRef.Custom(TestGames.alpha, second))
    private val names = mapOf(first to LegName("Crown jewels", "Cleo"), second to LegName("Moon", "Cleo"))

    @Before
    fun startKoinWithTheTestGames() {
        startKoin {
            modules(
                module {
                    single { TestGames.registry(listOf(TestGames.alpha)) }
                    single { GamePlayRegistry(emptyList()) }
                },
            )
        }
    }

    @After
    fun stopKoinAgain() = stopKoin()

    private fun show(boards: LegBoards) {
        val state = DuelPageFixtures.open(refs, doneLegs = emptySet()).copy(names = names)
        rule.setContent {
            PuzzleBoxTheme {
                DuelLegs(
                    state = state,
                    layout = PlayLayout.SinglePane,
                    now = DuelPageFixtures.NOW,
                    time = CompeteTime(Clock.fixed(DuelPageFixtures.NOW, ZoneOffset.UTC), Locale.US),
                    actions = LegsActions({}, {}, {}, {}),
                    boards = boards,
                )
            }
        }
        rule.waitForIdle()
    }

    private val board = LegBoard.Of(StubGivens().givens(Puzzle(TestGames.alpha, VariantId("v"), 1, byteArrayOf())))

    @Test
    fun aCustomPuzzleShowsItsTitleAndTheGameAuthorAndCodeUnderIt() {
        show(boards = LegBoards { board })

        rule.onNodeWithTag("duel.legs.title.0").assertTextEquals("Crown jewels")
        rule.onNodeWithTag("duel.legs.subtitle.0").assertTextEquals("Alpha · by Cleo · ${first.display}")
    }

    @Test
    fun aTakenDownPuzzleNeverSavedShowsWhyAndStartIsDisabled() {
        show(boards = LegBoards { if ((it as LegRef.Custom).code == first) LegBoard.TakenDown else board })

        rule.onNodeWithText("This puzzle was taken down by its author").assertExists()
        rule.onNodeWithTag("duel.legs.action.0").assertIsNotEnabled()
        rule.onNodeWithTag("duel.legs.action.1").assertIsEnabled()
    }

    @Test
    fun anOfflineUnopenedPuzzleShowsRetryAndARetryThatAnswersClearsIt() {
        var online = false
        show(boards = LegBoards { if ((it as LegRef.Custom).code == first && !online) LegBoard.Offline else board })
        rule.onNodeWithText("You're offline. Connect and try again.").assertExists()
        rule.onNodeWithTag("duel.legs.action.0").assertIsNotEnabled()

        online = true
        rule.onNodeWithTag("duel.legs.retry.0").performClick()
        rule.waitForIdle()

        rule.onAllNodesWithTag("duel.legs.retry.0").assertCountEquals(0)
        rule.onAllNodesWithTag("duel.legs.offline.0").assertCountEquals(0)
        rule.onNodeWithTag("duel.legs.action.0").assertIsEnabled()
    }

    @Test
    fun aSlowPuzzleNeverHoldsUpTheOthers() {
        show(boards = LegBoards { if ((it as LegRef.Custom).code == first) kotlinx.coroutines.awaitCancellation() else LegBoard.TakenDown })

        rule.onNodeWithTag("duel.legs.action.1").assertIsNotEnabled()
        rule.onNodeWithTag("duel.legs.action.0").assertIsEnabled()
    }
}
