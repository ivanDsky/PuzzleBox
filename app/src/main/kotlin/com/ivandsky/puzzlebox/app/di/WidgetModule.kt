package com.ivandsky.puzzlebox.app.di

import com.ivandsky.puzzlebox.app.widget.AndroidWidgetComponentSwitch
import com.ivandsky.puzzlebox.app.widget.AndroidWidgetRefresher
import com.ivandsky.puzzlebox.app.widget.AndroidWidgetRolloverAlarm
import com.ivandsky.puzzlebox.app.widget.ArmWidgetRollover
import com.ivandsky.puzzlebox.app.widget.BuildWidgetState
import com.ivandsky.puzzlebox.app.widget.PuzzleBoxWidget
import com.ivandsky.puzzlebox.app.widget.WidgetAppHooks
import com.ivandsky.puzzlebox.app.widget.WidgetComponentSwitch
import com.ivandsky.puzzlebox.app.widget.WidgetRefresher
import com.ivandsky.puzzlebox.app.widget.WidgetRolloverAlarm
import com.ivandsky.puzzlebox.app.widget.WidgetStateReader
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.common.flags.isEnabled
import com.ivandsky.puzzlebox.domain.ObserveGameStats
import com.ivandsky.puzzlebox.domain.ObserveTodaySet
import com.ivandsky.puzzlebox.game.Cadence
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.play.flags.PlayFlag
import java.time.Clock
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
 * The home widget's bindings: the reader over the same use cases Today uses, the widget, the redraw seam, the rollover
 * wake-up and the app-start hook. The flag is read here and nowhere else.
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
    single<WidgetRolloverAlarm> { AndroidWidgetRolloverAlarm(androidContext()) }
    single {
        val enabled = get<ReleaseFlags>().isEnabled(PlayFlag.HomeWidget)
        ArmWidgetRollover(
            // The zones of the listed games' daily cadences. With the flag off there are none, so a wake-up that was already
            // pending when the flag went off redraws once and does not re-arm itself.
            zones = {
                if (enabled) {
                    get<GameRegistry>().all.map { it.definition.cadence }.filterIsInstance<Cadence.Daily>().map { it.zone }
                } else {
                    emptyList()
                }
            },
            alarm = get(),
            clock = Clock.systemUTC(),
        )
    }
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
            armRollover = get(),
            alarm = get(),
            application = androidApplication(),
            scope = get<CoroutineScope>(),
        )
    }
}
