package com.ivandsky.puzzlebox.compete.create

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.compete.create.lineup.LineupDraft
import com.ivandsky.puzzlebox.compete.create.lineup.LineupEditor
import com.ivandsky.puzzlebox.domain.lineup.AnyGame
import com.ivandsky.puzzlebox.compete.create.lineup.AddPuzzleViewModel
import com.ivandsky.puzzlebox.compete.create.lineup.PAST_YOU_PICKER
import com.ivandsky.puzzlebox.compete.create.lineup.SOLVED_ROWS
import com.ivandsky.puzzlebox.compete.create.lineup.pickerOf
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.GamePlayController
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.gameui.TutorialStep
import com.ivandsky.puzzlebox.domain.lineup.ObserveLegTitles
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.repository.FakePublishedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeReceivedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository

// The New duel sheet's states, shared by its goldens and its behaviour tests.

/** Leg titles over empty repositories: the view model tests that never share an invite. */
internal fun noLegTitles() = ObserveLegTitles(FakeReceivedPuzzleRepository(), FakePublishedPuzzleRepository(), FakeSharedPuzzleRepository())

internal val ZIP = GameId("zip")
internal val TANGO = GameId("tango")
internal val QUEENS = GameId("queens")
internal val MINI = GameId("mini-sudoku")
internal val STRANDS = GameId("strands")

internal val GAMES = listOf(
    DuelGame(ZIP, "Zip", todayNumber = 7, solvedToday = false),
    DuelGame(TANGO, "Tango", todayNumber = 7, solvedToday = false),
    DuelGame(QUEENS, "Queens", todayNumber = 7, solvedToday = false),
    DuelGame(MINI, "Mini Sudoku", todayNumber = 7, solvedToday = false),
    DuelGame(STRANDS, "Strands", todayNumber = 7, solvedToday = false),
)

internal val ALL_SOLVED = SolvedCounts(mapOf(ZIP to 17, TANGO to 11, QUEENS to 12, MINI to 6))

internal fun state(
    opponent: Opponent,
    self: SelfDuelView,
    summary: String,
) = NewDuelState(
    draft = DuelDraft.quick(GAMES),
    games = GAMES,
    summary = summary,
    invitee = null,
    step = DuelStep.Create,
    creating = false,
    notice = null,
    signIn = DuelSignIn.Idle,
    selfOffered = true,
    opponent = opponent,
    selfName = "Ivan",
    self = self,
)

/** Past you's state: Customize shows one "Any" row per chosen game. */
internal fun selfState(form: SelfDuelForm, counts: SolvedCounts) =
    SelfDuelView(form, counts, GAMES).let { state(Opponent.PastYou, it, it.summary).copy(lineup = LineupEditor(LineupDraft(form.games.map(::AnyGame)))) }

internal fun friend() = state(Opponent.Friend, SelfDuelView.initial(), "Best of 3 · mixed past dailies · ends Wed 18:00")

internal fun quick() = selfState(SelfDuelForm.quick(GAMES), ALL_SOLVED)

internal fun custom() = selfState(
    SelfDuelForm(DuelPreset.Custom, count = 5, games = setOf(ZIP, TANGO, QUEENS, MINI), includeFriends = true),
    SolvedCounts(ALL_SOLVED.byGame + (STRANDS to 0)),
)

internal fun fewSolved() = selfState(
    SelfDuelForm(DuelPreset.Custom, count = 3, games = setOf(ZIP), includeFriends = false),
    SolvedCounts(mapOf(ZIP to 2, TANGO to 1)),
)

internal fun noneSolved() = selfState(
    SelfDuelForm(DuelPreset.Custom, count = 3, games = setOf(STRANDS), includeFriends = false),
    SolvedCounts(mapOf(ZIP to 14, TANGO to 9, QUEENS to 11, MINI to 6)),
)

/** Actions that do nothing, but [onCreate]. */
internal fun actionsWith(onCreate: () -> Unit = {}) = NewDuelActions(
    onQuick = {}, onDaily = {}, onToggle = {}, onDeadline = {}, onFriends = {}, onOpponent = {},
    onCreate = onCreate, onGoogle = {}, onCancel = {},
)

internal val NO_ACTIONS = actionsWith()

internal fun newDuelBrands(): GamePlayRegistry = GamePlayRegistry(
    listOf(
        BrandOnly(ZIP, AccentSlot.Blue),
        BrandOnly(TANGO, AccentSlot.Orange),
        BrandOnly(QUEENS, AccentSlot.Violet),
        BrandOnly(MINI, AccentSlot.Pink),
        BrandOnly(STRANDS, AccentSlot.Teal),
    ),
)

/** The brands and Past you's add-puzzle view model, which Customize's lineup panel resolves from Koin. */
internal fun newDuelModule() = module {
    single { newDuelBrands() }
    viewModel(PAST_YOU_PICKER) { AddPuzzleViewModel(pickerOf(SOLVED_ROWS), lookup = null, pastYou = true) }
}

/** The games' accent hues without the games: only the brand is real, the play surface is never shown. */
internal class BrandOnly(override val gameId: GameId, accent: AccentSlot) : GamePlayUi {
    override val brand: GameBrand = GameBrand.Neutral.copy(accent = accent)
    override val tutorial: List<TutorialStep> = emptyList()

    override fun createController(env: PlayEnvironment): GamePlayController = throw UnsupportedOperationException("brand only")

    @Composable
    override fun Board(controller: GamePlayController, modifier: Modifier) = Unit

    @Composable
    override fun Screen(
        controller: GamePlayController,
        status: PlayBoardStatus,
        layout: PlayLayout,
        sidePanel: (@Composable ColumnScope.() -> Unit)?,
        onRetry: () -> Unit,
        onSolved: (timeMs: Long, hintsUsed: Int) -> Unit,
        modifier: Modifier,
    ) = Unit
}

