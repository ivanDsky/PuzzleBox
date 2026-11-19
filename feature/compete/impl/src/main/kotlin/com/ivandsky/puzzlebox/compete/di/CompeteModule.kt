package com.ivandsky.puzzlebox.compete.di

import com.ivandsky.puzzlebox.common.di.flagSet
import com.ivandsky.puzzlebox.compete.CompeteFlag
import com.ivandsky.puzzlebox.compete.create.NewDuelViewModel
import com.ivandsky.puzzlebox.compete.create.lineup.AddPuzzleViewModel
import com.ivandsky.puzzlebox.compete.create.lineup.PAST_YOU_PICKER
import com.ivandsky.puzzlebox.compete.duel.DuelViewModel
import com.ivandsky.puzzlebox.compete.reveal.RematchViewModel
import com.ivandsky.puzzlebox.compete.reveal.RevealViewModel
import com.ivandsky.puzzlebox.compete.tab.DuelsTabViewModel
import com.ivandsky.puzzlebox.data.match.Match
import com.ivandsky.puzzlebox.data.repository.UserPreferencesRepository
import com.ivandsky.puzzlebox.domain.ObserveMatch
import com.ivandsky.puzzlebox.domain.lineup.LoadPuzzleByCode
import com.ivandsky.puzzlebox.domain.lineup.ObservePickablePuzzles
import com.ivandsky.puzzlebox.domain.lineup.ObserveSolvedPuzzles
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Compete feature wiring. [com.ivandsky.puzzlebox.domain.ObserveMyDuels], ObserveMatch, ObserveDuel, UpdateMatchSeat,
 * RecordRevealSeen and LoadSolveReplay come from :core:domain's domainModule and the identity service from the app's aggregation; the
 * Duels tab's, the New duel sheet's, the add-puzzle searches' (a friend's, and Past you's under its own qualifier), the duel screen's, the reveal's and the rematch sheet's view models are bound
 * here (the duel screen's by its code; the reveal's by its code and the screen's reduced motion, zone and locale; the
 * rematch's by the duel it repeats). The result sync comes from the app, which binds it to the result poster. The feature's
 * release flags ([CompeteFlag]) are declared here; the view models that read them take the app's ReleaseFlags.
 */
val competeModule: Module = module {
    flagSet("compete", CompeteFlag.entries)
    viewModel {
        DuelsTabViewModel(observeMyDuels = get(), updateSeat = get(), identity = get(), clock = { Instant.now() }, releaseFlags = get())
    }
    viewModel {
        NewDuelViewModel(
            createMatch = get(),
            identity = get(),
            idTokens = get(),
            todaySet = get(),
            calendar = get(),
            daily = get(),
            registry = get(),
            createSelfDuel = get(),
            computeSolvedPool = get(),
            clock = { Instant.now() },
            zone = { ZoneId.systemDefault() },
            locale = { Locale.getDefault() },
            legTitles = get(),
            releaseFlags = get(),
        )
    }
    viewModel { AddPuzzleViewModel(observe = get<ObservePickablePuzzles>()::invoke, lookup = get<LoadPuzzleByCode>()::invoke) }
    // Past you's add sheet: the same search over what the player solved, on the device, so no code lookup.
    viewModel(PAST_YOU_PICKER) { AddPuzzleViewModel(observe = get<ObserveSolvedPuzzles>()::invoke, lookup = null, pastYou = true) }
    viewModel { (code: String) ->
        DuelViewModel(
            code = code,
            observeDuel = get(),
            updateSeat = get(),
            resultSync = get(),
            identity = get(),
            idTokens = get(),
            registry = get(),
            deleteSelfDuel = get(),
            showTimeLeft = get<UserPreferencesRepository>().showSelfDuelTimeLeft,
            loadLegPuzzle = get(),
            plays = get(),
            dispatchers = get(),
            legTitles = get(),
            saveDuelPuzzles = get(),
            releaseFlags = get(),
        )
    }
    viewModel { (code: String, reducedMotion: Boolean, zone: ZoneId, locale: Locale) ->
        RevealViewModel(
            code = code,
            reducedMotion = reducedMotion,
            zone = zone,
            locale = locale,
            observeMatch = get<ObserveMatch>()::invoke,
            registry = get(),
            brands = get(),
            recordSeen = get(),
            loadReplay = get(),
            identity = get(),
            releaseFlags = get(),
        )
    }
    viewModel { (match: Match) -> RematchViewModel(match = match, createMatch = get(), createSelfDuel = get()) }
}
