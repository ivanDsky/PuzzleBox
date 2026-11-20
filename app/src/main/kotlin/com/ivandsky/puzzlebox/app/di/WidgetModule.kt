package com.ivandsky.puzzlebox.app.di

import com.ivandsky.puzzlebox.app.widget.AndroidWidgetRefresher
import com.ivandsky.puzzlebox.app.widget.BuildWidgetState
import com.ivandsky.puzzlebox.app.widget.PuzzleBoxWidget
import com.ivandsky.puzzlebox.app.widget.WidgetRefresher
import com.ivandsky.puzzlebox.app.widget.WidgetStateReader
import com.ivandsky.puzzlebox.domain.ObserveGameStats
import com.ivandsky.puzzlebox.domain.ObserveTodaySet
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** The home widget's bindings: the reader over the same use cases Today uses, the widget and the redraw seam. It reads no flag. */
val widgetModule = module {
    // Resolved on each read: the use cases are factories, and nothing here builds them at app start.
    single {
        WidgetStateReader(
            todaySet = { get<ObserveTodaySet>()() },
            stats = { get<ObserveGameStats>()() },
            build = BuildWidgetState(),
        )
    }
    single { PuzzleBoxWidget(reader = get()) }
    single<WidgetRefresher> { AndroidWidgetRefresher(androidContext(), widget = get()) }
}
