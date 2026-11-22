package com.ivandsky.puzzlebox.insights.di

import com.ivandsky.puzzlebox.insights.domain.rankhistory.ObserveRankHistory
import org.koin.dsl.module

/** The kept weeks' reader, included by [insightsModule]: [ObserveRankHistory] is the one reader of the rank history. */
val rankHistoryModule = module {
    factory { ObserveRankHistory(identity = get(), store = get()) }
}
