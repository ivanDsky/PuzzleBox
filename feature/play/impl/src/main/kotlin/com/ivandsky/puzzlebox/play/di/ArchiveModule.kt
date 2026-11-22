package com.ivandsky.puzzlebox.play.di

import com.ivandsky.puzzlebox.play.domain.LoadPastDay
import com.ivandsky.puzzlebox.play.domain.ObserveArchiveMonth
import com.ivandsky.puzzlebox.play.domain.ObserveArchiveProgress
import org.koin.dsl.module

/** The archive's use cases, included by [playModule]: the month and progress, and a past day's [LoadPastDay]. */
val archiveModule = module {
    factory { ObserveArchiveMonth(daily = get(), saved = get(), completions = get(), calendar = get()) }
    factory { ObserveArchiveProgress(daily = get(), calendar = get(), observeGameHistory = get()) }
    factory { LoadPastDay(daily = get(), registry = get()) }
}
