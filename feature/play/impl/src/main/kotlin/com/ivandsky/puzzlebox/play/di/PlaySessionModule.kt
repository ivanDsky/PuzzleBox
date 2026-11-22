package com.ivandsky.puzzlebox.play.di

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.common.flags.isEnabled
import com.ivandsky.puzzlebox.data.repository.SavedPlayRepository
import com.ivandsky.puzzlebox.data.repository.SolveCounterSwitch
import com.ivandsky.puzzlebox.play.PlaySessionStore
import com.ivandsky.puzzlebox.play.SlotOpening
import com.ivandsky.puzzlebox.play.SlotWriter
import com.ivandsky.puzzlebox.play.WriteLane
import com.ivandsky.puzzlebox.play.flags.PlayFlag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.koin.dsl.module

/**
 * The live play session's wiring, included by [playModule]: [PlaySessionStore] is a single so live plays outlive their
 * entries' compositions. Its slot opening and every slot's writer share ONE [WriteLane], so a released slot's final
 * save is on disk before the same slot's reopen reads it; the writers' scope is process lifetime, so a release never
 * cancels a queued save. The solve counter's switch is here too.
 */
val playSessionModule = module {
    // The backend's board submit asks at each write, so a flag flipped in debug Settings applies to the next solve.
    single<SolveCounterSwitch> {
        val flags = get<ReleaseFlags>()
        SolveCounterSwitch { flags.isEnabled(PlayFlag.SolveCounter) }
    }
    single {
        val lane = WriteLane()
        val saved = get<SavedPlayRepository>()
        val dispatchers = get<DispatcherProvider>()
        val writes = CoroutineScope(SupervisorJob() + dispatchers.main)
        PlaySessionStore(
            games = get(),
            ui = get(),
            opening = SlotOpening(
                daily = get(),
                pastDays = get(),
                saved = saved,
                calendar = get(),
                games = get(),
                lane = lane,
                received = get(),
                matches = get(),
                loadShared = get(),
                dispatchers = dispatchers,
            ),
            writers = { slot -> SlotWriter(slot, saved, lane, writes) },
            completions = get(),
            finishLeg = get(),
            calendar = get(),
            dispatchers = dispatchers,
        )
    }
}
