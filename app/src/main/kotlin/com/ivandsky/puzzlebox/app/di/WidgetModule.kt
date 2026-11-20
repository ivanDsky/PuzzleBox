package com.ivandsky.puzzlebox.app.di

import com.ivandsky.puzzlebox.app.widget.AndroidWidgetComponentSwitch
import com.ivandsky.puzzlebox.app.widget.AndroidWidgetRefresher
import com.ivandsky.puzzlebox.app.widget.BuildWidgetState
import com.ivandsky.puzzlebox.app.widget.PuzzleBoxWidget
import com.ivandsky.puzzlebox.app.widget.WidgetAppHooks
import com.ivandsky.puzzlebox.app.widget.WidgetComponentSwitch
import com.ivandsky.puzzlebox.app.widget.WidgetRefresher
import com.ivandsky.puzzlebox.app.widget.WidgetStateReader
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.common.flags.isEnabled
import com.ivandsky.puzzlebox.domain.ObserveGameStats
import com.ivandsky.puzzlebox.domain.ObserveTodaySet
import com.ivandsky.puzzlebox.play.flags.PlayFlag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * The home widget's bindings: the reader over the same use cases Today uses, the widget, the redraw seam and the app-start
 * hook, which reads the flag here and nowhere else.
 */
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
    single<WidgetComponentSwitch> { AndroidWidgetComponentSwitch(androidContext()) }
    single {
        WidgetAppHooks(
            enabled = get<ReleaseFlags>().isEnabled(PlayFlag.HomeWidget),
            componentSwitch = get(),
            // Today's set or the stats moving. The use cases are built when the collection starts, off the app start.
            changes = flow {
                emitAll(
                    combine(get<ObserveTodaySet>()(), get<ObserveGameStats>()()) { today, stats -> today to stats }
                        .distinctUntilChanged()
                        .map { },
                )
            },
            refresher = get(),
            application = androidApplication(),
            scope = get<CoroutineScope>(),
        )
    }
}
