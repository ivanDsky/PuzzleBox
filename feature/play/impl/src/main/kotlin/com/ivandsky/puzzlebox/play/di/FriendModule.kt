package com.ivandsky.puzzlebox.play.di

import com.ivandsky.puzzlebox.domain.ComputeCustomBoard
import com.ivandsky.puzzlebox.domain.LoadSharedPuzzle
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.play.api.CustomResultKey
import com.ivandsky.puzzlebox.play.api.FriendPuzzleKey
import com.ivandsky.puzzlebox.play.friend.CustomResultViewModel
import com.ivandsky.puzzlebox.play.friend.EnterCodeViewModel
import com.ivandsky.puzzlebox.play.friend.FriendPuzzleBoard
import com.ivandsky.puzzlebox.play.friend.FriendPuzzleViewModel
import java.time.Instant
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

/**
 * Friend puzzles' wiring, included by [playModule]: opening a code ([LoadSharedPuzzle], whose
 * re-check of the board stops after [OPEN_BUDGET_MS]), the Enter code sheet's check, and the
 * card's ViewModel and the result's, each reading the puzzle's board through its own
 * [FriendPuzzleBoard], which ranks it with [ComputeCustomBoard].
 * The games are reached only through the app's [com.ivandsky.puzzlebox.game.GameRegistry];
 * this module names none.
 */
val friendModule = module {
    factory {
        LoadSharedPuzzle(
            registry = get(),
            shared = get(),
            received = get(),
            budget = { Budget.within(OPEN_BUDGET_MS, System::nanoTime) },
            clock = { Instant.now() },
        )
    }
    viewModel { EnterCodeViewModel(get(), get()) }
    factory { ComputeCustomBoard() }
    factory { FriendPuzzleBoard(get(), get(), get(), get()) }
    viewModel { (key: FriendPuzzleKey) -> FriendPuzzleViewModel(key, get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { (key: CustomResultKey) -> CustomResultViewModel(key, get(), get(), get(), get(), get(), get(), get()) }
}

private const val OPEN_BUDGET_MS = 5_000L
