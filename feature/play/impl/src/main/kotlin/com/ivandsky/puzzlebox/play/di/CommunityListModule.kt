package com.ivandsky.puzzlebox.play.di

import com.ivandsky.puzzlebox.play.domain.ListCommunityPuzzles
import com.ivandsky.puzzlebox.play.domain.ReportPuzzle
import org.koin.dsl.module

/** The community list and Report, included by [playModule]: [ListCommunityPuzzles] and [ReportPuzzle]. */
val communityListModule = module {
    factory { ReportPuzzle(shared = get(), hidden = get()) }
    factory { ListCommunityPuzzles(shared = get(), hidden = get(), identity = get(), clock = System::currentTimeMillis) }
}
