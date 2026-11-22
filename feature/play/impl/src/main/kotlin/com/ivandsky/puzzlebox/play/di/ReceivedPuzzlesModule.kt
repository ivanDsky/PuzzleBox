package com.ivandsky.puzzlebox.play.di

import com.ivandsky.puzzlebox.play.domain.DeleteReceivedPuzzle
import com.ivandsky.puzzlebox.play.domain.ObserveFriendPuzzles
import org.koin.dsl.module

/** Received friend puzzles' use cases, included by [playModule]: [ObserveFriendPuzzles] and [DeleteReceivedPuzzle]. */
val receivedPuzzlesModule = module {
    factory { ObserveFriendPuzzles(received = get(), saved = get(), completions = get(), registry = get(), hidden = get()) }
    factory { DeleteReceivedPuzzle(received = get(), saved = get()) }
}
