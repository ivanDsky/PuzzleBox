package com.ivandsky.puzzlebox.compete.di

import com.ivandsky.puzzlebox.common.di.flagSet
import com.ivandsky.puzzlebox.compete.CompeteFlag
import com.ivandsky.puzzlebox.compete.create.NewDuelViewModel
import com.ivandsky.puzzlebox.compete.create.lineup.AddPuzzleViewModel
import com.ivandsky.puzzlebox.compete.create.lineup.PAST_YOU_PICKER
import com.ivandsky.puzzlebox.compete.domain.CreateSelfDuel
import com.ivandsky.puzzlebox.compete.domain.DeleteSelfDuel
import com.ivandsky.puzzlebox.compete.domain.ObserveDuel
import com.ivandsky.puzzlebox.compete.domain.ObserveMyDuels
import com.ivandsky.puzzlebox.compete.domain.RecordRevealSeen
import com.ivandsky.puzzlebox.compete.domain.UpdateMatchSeat
import com.ivandsky.puzzlebox.compete.domain.lineup.CommunitySource
import com.ivandsky.puzzlebox.compete.domain.lineup.DailyArchiveSource
import com.ivandsky.puzzlebox.compete.domain.lineup.FriendSource
import com.ivandsky.puzzlebox.compete.domain.lineup.LoadPuzzleByCode
import com.ivandsky.puzzlebox.compete.domain.lineup.ObserveLegTitles
import com.ivandsky.puzzlebox.compete.domain.lineup.ObservePickablePuzzles
import com.ivandsky.puzzlebox.compete.domain.lineup.ObserveSolvedPuzzles
import com.ivandsky.puzzlebox.compete.domain.lineup.OwnSource
import com.ivandsky.puzzlebox.compete.domain.lineup.SaveDuelPuzzles
import com.ivandsky.puzzlebox.compete.duel.DuelViewModel
import com.ivandsky.puzzlebox.compete.reveal.RematchViewModel
import com.ivandsky.puzzlebox.compete.reveal.RevealViewModel
import com.ivandsky.puzzlebox.compete.tab.DuelsTabViewModel
import com.ivandsky.puzzlebox.data.match.Match
import com.ivandsky.puzzlebox.data.repository.UserPreferencesRepository
import com.ivandsky.puzzlebox.domain.LoadSharedPuzzle
import com.ivandsky.puzzlebox.domain.ObserveMatch
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.random.Random
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Compete feature wiring. ObserveMatch and LoadSolveReplay come from :core:domain's domainModule and the identity
 * service from the app's aggregation. Compete's own use cases are defined here: the Duels tab's rows
 * ([ObserveMyDuels]), the duel page ([ObserveDuel]), its seat moves ([UpdateMatchSeat]) and self-duel removal
 * ([DeleteSelfDuel]), the reveal's seen mark ([RecordRevealSeen]), the leg titles and saved duel puzzles
 * ([ObserveLegTitles], [SaveDuelPuzzles]), the self-duel create ([CreateSelfDuel]), and the add-puzzle searches and
 * code lookup ([ObservePickablePuzzles] over the community, friend, own and daily-archive sources,
 * [ObserveSolvedPuzzles], [LoadPuzzleByCode]). The Duels tab's, the New duel sheet's, the add-puzzle searches' (a
 * friend's, and Past you's under its own qualifier), the duel screen's, the reveal's and the rematch sheet's view
 * models are bound here (the duel screen's by its code; the reveal's by its code and the screen's reduced motion,
 * zone and locale; the rematch's by the duel it repeats). The result sync comes from the app, which binds it to the
 * result poster. The feature's release flags ([CompeteFlag]) are declared here; the view models that read them take
 * the app's ReleaseFlags.
 */
val competeModule: Module = module {
    flagSet("compete", CompeteFlag.entries)
    factory {
        ObserveMyDuels(repo = get(), self = get(), outbox = get(), local = get(), identity = get(), clock = { Instant.now() })
    }
    factory { ObserveDuel(observeMatch = get(), completions = get(), savedPlays = get()) }
    factory { SaveDuelPuzzles(open = get<LoadSharedPuzzle>()::invoke) }
    factory { DeleteSelfDuel(self = get(), outbox = get(), plays = get(), local = get()) }
    factory { UpdateMatchSeat(repo = get(), local = get(), identity = get()) }
    factory { RecordRevealSeen(local = get(), clock = { Instant.now() }) }
    factory { ObserveLegTitles(received = get(), published = get(), shared = get()) }
    factory {
        CreateSelfDuel(
            pool = get(),
            completions = get(),
            received = get(),
            self = get(),
            local = get(),
            clock = { Instant.now() },
            random = Random.Default,
        )
    }
    factory { LoadPuzzleByCode(load = get<LoadSharedPuzzle>()::invoke, dispatchers = get()) }
    single { CommunitySource(shared = get(), hidden = get(), identity = get(), registry = get(), clock = System::currentTimeMillis) }
    factory {
        ObservePickablePuzzles(
            completions = get(),
            community = get(),
            friends = FriendSource(received = get(), identity = get(), clock = System::currentTimeMillis),
            own = OwnSource(published = get(), identity = get()),
            archive = DailyArchiveSource(daily = get(), registry = get()),
        )
    }
    factory { ObserveSolvedPuzzles(registry = get(), pool = get(), completions = get(), received = get()) }
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
