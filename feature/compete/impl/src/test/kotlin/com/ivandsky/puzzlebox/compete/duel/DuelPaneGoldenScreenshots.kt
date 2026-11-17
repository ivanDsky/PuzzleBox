package com.ivandsky.puzzlebox.compete.duel

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.ivandsky.puzzlebox.compete.tab.settleAndCapture
import com.ivandsky.puzzlebox.compete.tab.showGolden
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.game.TestGames
import java.time.Clock
import java.time.LocalDate
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
import org.robolectric.annotation.GraphicsMode

private const val GOLDEN_TABLET = "w1280dp-h800dp-land-mdpi"

/**
 * A duel's page in the tablet's centred two columns, light and dark, with the selected puzzle's blurred board over its
 * details: a self duel played in full (See results, Remove duel, the DONE card, a solved puzzle's sharp board), a duel
 * with Sam still open, and a self duel with a puzzle to play. Goldens in `snapshots/duel-pane/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_TABLET)
class DuelPaneGoldenScreenshots {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val zip = GameId("Zip")
    private val sudoku = GameId("Mini Sudoku")
    private val strands = GameId("Strands")
    private val day = LocalDate.of(2026, 11, 9)
    private val refs = listOf(LegRef.Daily(zip, day, 583), LegRef.Daily(sudoku, day, 371), LegRef.Daily(strands, day, 412))
    private val stubs = mapOf(zip to StubGivens(7, 7), sudoku to StubGivens(6, 6), strands to StubGivens(8, 6))

    @Before
    fun startKoinWithTheGoldenGames() {
        startKoin {
            modules(
                module {
                    single { GameRegistry(listOf(TestGames.plugin(zip), TestGames.plugin(sudoku), TestGames.plugin(strands))) }
                    single {
                        GamePlayRegistry(
                            listOf(
                                BoardedPlay(zip, AccentSlot.Blue, stubs.getValue(zip)),
                                BoardedPlay(sudoku, AccentSlot.Teal, stubs.getValue(sudoku)),
                                BoardedPlay(strands, AccentSlot.Pink, stubs.getValue(strands)),
                            ),
                        )
                    }
                },
            )
        }
    }

    @After
    fun stopKoinAgain() = stopKoin()

    @Test fun `played all light`() = page("duel-pane-played-all", DuelPageFixtures.self(refs, setOf(0, 1, 2)), dark = false)
    @Test fun `played all dark`() = page("duel-pane-played-all", DuelPageFixtures.self(refs, setOf(0, 1, 2)), dark = true)

    @Test fun `open light`() = page("duel-pane-open", DuelPageFixtures.open(refs, emptySet()), dark = false)
    @Test fun `open dark`() = page("duel-pane-open", DuelPageFixtures.open(refs, emptySet()), dark = true)

    @Test fun `self your move light`() = page("duel-pane-self-your-move", DuelPageFixtures.self(refs, setOf(0)), dark = false)
    @Test fun `self your move dark`() = page("duel-pane-self-your-move", DuelPageFixtures.self(refs, setOf(0)), dark = true)

    private fun page(name: String, state: DuelUiState.Participant, dark: Boolean) {
        val boards = LegBoards { leg ->
            LegBoard.Of(stubs.getValue(leg.gameId).givens(Puzzle(leg.gameId, VariantId("v"), 1, byteArrayOf())))
        }
        rule.showGolden(dark) {
            DuelLegs(
                state = state,
                layout = PlayLayout.TwoPane,
                now = DuelPageFixtures.NOW,
                time = CompeteTime(Clock.fixed(DuelPageFixtures.NOW, ZoneOffset.UTC), Locale.US),
                actions = LegsActions({}, {}, {}, {}),
                modifier = Modifier.fillMaxSize(),
                boards = boards,
            )
        }
        rule.settleAndCapture("duel-pane", name, "tablet", dark)
    }
}
