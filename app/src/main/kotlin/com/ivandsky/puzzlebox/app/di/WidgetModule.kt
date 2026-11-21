package com.ivandsky.puzzlebox.app.di

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import com.ivandsky.puzzlebox.app.widget.AndroidWidgetComponentSwitch
import com.ivandsky.puzzlebox.app.widget.AndroidWidgetRefresher
import com.ivandsky.puzzlebox.app.widget.AndroidWidgetRolloverAlarm
import com.ivandsky.puzzlebox.app.widget.ArmWidgetRollover
import com.ivandsky.puzzlebox.app.widget.BuildWidgetState
import com.ivandsky.puzzlebox.app.widget.PuzzleBoxWidget
import com.ivandsky.puzzlebox.app.widget.PuzzleBoxWidgetReceiver
import com.ivandsky.puzzlebox.app.widget.ReadWidgetScoreboard
import com.ivandsky.puzzlebox.app.widget.WidgetAppHooks
import com.ivandsky.puzzlebox.app.widget.WidgetComponentSwitch
import com.ivandsky.puzzlebox.app.widget.WidgetRefresher
import com.ivandsky.puzzlebox.app.widget.WidgetRolloverAlarm
import com.ivandsky.puzzlebox.app.widget.WidgetStateReader
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.domain.GetNextRollover
import com.ivandsky.puzzlebox.domain.GetPuzzleDay
import com.ivandsky.puzzlebox.domain.ObserveGameStats
import com.ivandsky.puzzlebox.domain.ObserveResultRank
import com.ivandsky.puzzlebox.domain.ObserveTodaySet
import com.ivandsky.puzzlebox.game.Cadence
import com.ivandsky.puzzlebox.game.GameRegistry
import java.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * The home widget's bindings: the reader over the same use cases Today uses plus the scoreboard's reads, the widget, the redraw seam, the rollover
 * wake-up and the app-start hook.
 */
val widgetModule = module {
    // Resolved on each read: the use cases are factories, and nothing here builds them at app start.
    single {
        WidgetStateReader(
            todaySet = { get<ObserveTodaySet>()() },
            stats = { get<ObserveGameStats>()() },
            scoreboard = ReadWidgetScoreboard(
                best = { get<CompletionRepository>().best(it) },
                records = { get<CompletionRepository>().observeRecords().first() },
                // No LinkedIn connections: the widget ranks on the PuzzleBox board alone, as Results does without them.
                rank = { game, day -> get<ObserveResultRank>()(game, day, connections = null) },
                puzzleDay = { get<GetPuzzleDay>()() },
                rankOrder = { get<GameRegistry>().rankOrderOf(it) },
                nextRollover = { get<GetNextRollover>()() },
            ),
            build = BuildWidgetState(),
        )
    }
    single { PuzzleBoxWidget(reader = get()) }
    single<WidgetRefresher> { AndroidWidgetRefresher(androidContext(), widget = get()) }
    single<WidgetComponentSwitch> { AndroidWidgetComponentSwitch(androidContext()) }
    single<WidgetRolloverAlarm> { AndroidWidgetRolloverAlarm(androidContext()) }
    single {
        ArmWidgetRollover(
            // The zones of the listed games' daily cadences.
            zones = { get<GameRegistry>().all.map { it.definition.cadence }.filterIsInstance<Cadence.Daily>().map { it.zone } },
            hasWidgets = {
                val context = androidContext()
                AppWidgetManager.getInstance(context)
                    .getAppWidgetIds(ComponentName(context, PuzzleBoxWidgetReceiver::class.java))
                    .isNotEmpty()
            },
            alarm = get(),
            clock = Clock.systemUTC(),
        )
    }
    single {
        WidgetAppHooks(
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
            application = androidApplication(),
            scope = get<CoroutineScope>(),
        )
    }
}
