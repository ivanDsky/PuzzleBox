package com.ivandsky.puzzlebox.domain.di

import com.ivandsky.puzzlebox.data.match.MatchLocalRepository
import com.ivandsky.puzzlebox.domain.ComputeMatchLegs
import com.ivandsky.puzzlebox.domain.ImportDraftFromText
import com.ivandsky.puzzlebox.domain.ComputeStreak
import com.ivandsky.puzzlebox.domain.CreateDraft
import com.ivandsky.puzzlebox.domain.CreateDraftCopy
import com.ivandsky.puzzlebox.domain.CreateMatch
import com.ivandsky.puzzlebox.domain.CreateSelfDuel
import com.ivandsky.puzzlebox.domain.DeleteSelfDuel
import com.ivandsky.puzzlebox.domain.ComputeSolvedPool
import com.ivandsky.puzzlebox.domain.GetFeaturedGame
import com.ivandsky.puzzlebox.domain.GetNextRollover
import com.ivandsky.puzzlebox.domain.LoadCustomReplay
import com.ivandsky.puzzlebox.domain.LoadDailyReplay
import com.ivandsky.puzzlebox.domain.LoadLegPuzzle
import com.ivandsky.puzzlebox.domain.LoadLegReplay
import com.ivandsky.puzzlebox.domain.LoadPlayerReplay
import com.ivandsky.puzzlebox.domain.LoadSharedPuzzle
import com.ivandsky.puzzlebox.domain.LoadSolveReplay
import com.ivandsky.puzzlebox.domain.ObserveDuel
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.ObserveGameStats
import com.ivandsky.puzzlebox.domain.ObserveLeaderboard
import com.ivandsky.puzzlebox.domain.ObserveMatch
import com.ivandsky.puzzlebox.domain.ObserveResultRank
import com.ivandsky.puzzlebox.domain.ObserveTodaySet
import com.ivandsky.puzzlebox.domain.RecordRevealSeen
import com.ivandsky.puzzlebox.domain.GetPuzzleDay
import com.ivandsky.puzzlebox.domain.lineup.CommunitySource
import com.ivandsky.puzzlebox.domain.lineup.DailyArchiveSource
import com.ivandsky.puzzlebox.domain.lineup.FriendSource
import com.ivandsky.puzzlebox.domain.lineup.LoadPuzzleByCode
import com.ivandsky.puzzlebox.domain.lineup.ObserveLegTitles
import com.ivandsky.puzzlebox.domain.lineup.ObservePickablePuzzles
import com.ivandsky.puzzlebox.domain.lineup.ObserveSolvedPuzzles
import com.ivandsky.puzzlebox.domain.lineup.OwnSource
import com.ivandsky.puzzlebox.domain.ValidatePublish
import com.ivandsky.puzzlebox.domain.SyncTodayPuzzles
import com.ivandsky.puzzlebox.domain.UpdateMatchSeat
import com.ivandsky.puzzlebox.domain.lineup.SaveDuelPuzzles
import com.ivandsky.puzzlebox.domain.lineup.ValidateExactPuzzles
import com.ivandsky.puzzlebox.domain.linkedin.ObserveLinkedInDay
import com.ivandsky.puzzlebox.domain.linkedin.ObserveLinkedInDayView
import com.ivandsky.puzzlebox.domain.linkedin.SyncLinkedInDay
import com.ivandsky.puzzlebox.domain.ranks.GetPlayerActions
import com.ivandsky.puzzlebox.domain.ranks.ObserveFriendDay
import com.ivandsky.puzzlebox.domain.ranks.ObserveFriendUids
import com.ivandsky.puzzlebox.domain.ranks.ObserveOverall
import com.ivandsky.puzzlebox.domain.ranks.ObserveRankWindow
import com.ivandsky.puzzlebox.domain.ranks.ObserveWindowRefresh
import com.ivandsky.puzzlebox.domain.ranks.SubmitDailyReplay
import java.time.Instant
import java.util.UUID
import kotlin.random.Random
import org.koin.dsl.module

/**
 * Use-case wiring: stateless factories over :core:data's repositories and the app's
 * [com.ivandsky.puzzlebox.game.GameRegistry], except [SyncTodayPuzzles] — a single, so
 * every surface reads the same failed fetches. Today's leaderboard day is the device's
 * local date, read when the board (re)starts. A new draft's id (blank or copied) is a random
 * UUID and its timestamp the wall clock. A duel's draw shuffles with [Random.Default].
 */
val domainModule = module {
    factory {
        ObserveGameHistory(
            registry = get(),
            daily = get(),
            completions = get(),
            duelOpponents = get<MatchLocalRepository>().opponentNames(),
        )
    }
    factory { GetFeaturedGame(registry = get()) }
    factory { GetPuzzleDay(registry = get()) }
    factory { GetNextRollover(registry = get()) }
    factory { ComputeStreak(puzzleDay = get()) }
    factory { ObserveGameStats(registry = get(), observeGameHistory = get(), computeStreak = get()) }
    single { SyncTodayPuzzles(registry = get(), daily = get(), calendar = get()) }
    factory {
        ObserveTodaySet(
            registry = get(),
            saved = get(),
            completions = get(),
            calendar = get(),
            daily = get(),
            sync = get(),
        )
    }
    factory { ObserveLeaderboard(identity = get(), leaderboard = get(), registry = get(), calendar = get()) }
    factory {
        ObserveRankWindow(identity = get(), leaderboard = get(), finalBoards = get(), registry = get(), calendar = get())
    }
    factory { ObserveWindowRefresh(network = get()) }
    // The app decides whether there is a Friends/Global setting to follow; with none the rank is Global.
    factory { ObserveResultRank(leaderboard = get(), registry = get(), friendDay = get<ObserveFriendDay.Binding>().reader) }
    factory {
        CreateDraft(
            registry = get(),
            drafts = get(),
            newId = { UUID.randomUUID().toString() },
            clock = { Instant.now() },
        )
    }
    factory { CreateDraftCopy(drafts = get(), newId = { UUID.randomUUID().toString() }, clock = { Instant.now() }) }
    factory {
        ObserveMatch(
            repo = get(),
            outbox = get(),
            local = get(),
            identity = get(),
            registry = get(),
            clock = { Instant.now() },
        )
    }
    factory { ObserveDuel(observeMatch = get(), completions = get(), savedPlays = get()) }
    factory { ObserveOverall(identity = get(), completions = get()) }
    factory { ObserveFriendUids(matches = get(), identity = get()) }
    factory { GetPlayerActions(visibility = get(), completions = get()) }
    factory {
        SubmitDailyReplay(
            completions = get(),
            identity = get(),
            calendar = get(),
            statuses = get(),
            visibility = get(),
            optIns = get(),
            replays = get(),
        )
    }
    factory { ValidateExactPuzzles(shared = get()) }
    factory { LoadPuzzleByCode(load = get<LoadSharedPuzzle>()::invoke, dispatchers = get()) }
    factory { SaveDuelPuzzles(open = get<LoadSharedPuzzle>()::invoke) }
    factory { CreateMatch(repo = get(), local = get(), draw = get(), identity = get(), random = Random.Default, validateExact = get()) }
    factory { ComputeSolvedPool(registry = get(), completions = get(), received = get()) }
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
    factory { DeleteSelfDuel(self = get(), outbox = get(), plays = get(), local = get()) }
    factory { UpdateMatchSeat(repo = get(), local = get(), identity = get()) }
    factory { RecordRevealSeen(local = get(), clock = { Instant.now() }) }
    factory { ImportDraftFromText(registry = get(), createDraft = get(), drafts = get(), dispatchers = get()) }
    factory { ValidatePublish() }
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
    factory { ObserveLegTitles(received = get(), published = get(), shared = get()) }
    factory {
        ComputeMatchLegs(
            registry = get(),
            daily = get(),
            calendar = get(),
            history = get(),
            received = get(),
            random = Random.Default,
        )
    }
    factory { SyncLinkedInDay(days = get()) }
    factory { ObserveLinkedInDay(days = get(), registry = get()) }
    factory { ObserveLinkedInDayView(observe = get(), sync = get(), link = get(), days = get()) }
    factory { LoadDailyReplay(completions = get(), daily = get()) }
    factory { LoadCustomReplay(completions = get(), received = get(), loadShared = get()) }
    factory {
        LoadLegReplay(
            matches = get(),
            outbox = get(),
            identity = get(),
            daily = get(),
            received = get(),
            loadShared = get(),
            published = get(),
        )
    }
    factory { LoadLegPuzzle(daily = get(), received = get(), loadShared = get(), published = get()) }
    factory { LoadPlayerReplay(replays = get(), daily = get()) }
    factory {
        LoadSolveReplay(
            games = get(),
            daily = get(),
            custom = get(),
            leg = get(),
            player = get(),
            completions = get(),
            dispatchers = get(),
        )
    }
}
