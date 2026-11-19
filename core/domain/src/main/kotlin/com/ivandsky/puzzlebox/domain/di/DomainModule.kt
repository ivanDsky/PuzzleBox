package com.ivandsky.puzzlebox.domain.di

import com.ivandsky.puzzlebox.data.match.MatchLocalRepository
import com.ivandsky.puzzlebox.domain.ComputeMatchLegs
import com.ivandsky.puzzlebox.domain.ComputeStreak
import com.ivandsky.puzzlebox.domain.CreateDraft
import com.ivandsky.puzzlebox.domain.CreateMatch
import com.ivandsky.puzzlebox.domain.GetFeaturedGame
import com.ivandsky.puzzlebox.domain.GetNextRollover
import com.ivandsky.puzzlebox.domain.GetPuzzleDay
import com.ivandsky.puzzlebox.domain.LoadCustomReplay
import com.ivandsky.puzzlebox.domain.LoadDailyReplay
import com.ivandsky.puzzlebox.domain.LoadLegPuzzle
import com.ivandsky.puzzlebox.domain.LoadLegReplay
import com.ivandsky.puzzlebox.domain.LoadPlayerReplay
import com.ivandsky.puzzlebox.domain.LoadSolveReplay
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.ObserveGameStats
import com.ivandsky.puzzlebox.domain.ObserveLeaderboard
import com.ivandsky.puzzlebox.domain.ObserveMatch
import com.ivandsky.puzzlebox.domain.ObserveResultRank
import com.ivandsky.puzzlebox.domain.ObserveTodaySet
import com.ivandsky.puzzlebox.domain.SyncTodayPuzzles
import com.ivandsky.puzzlebox.domain.insights.di.insightsDomainModule
import com.ivandsky.puzzlebox.domain.lineup.ValidateExactPuzzles
import com.ivandsky.puzzlebox.domain.linkedin.ObserveLinkedInDay
import com.ivandsky.puzzlebox.domain.linkedin.ObserveLinkedInDayView
import com.ivandsky.puzzlebox.domain.linkedin.SyncLinkedInDay
import com.ivandsky.puzzlebox.domain.ranks.ObserveFriendBoards
import com.ivandsky.puzzlebox.domain.ranks.ObserveFriendDay
import com.ivandsky.puzzlebox.domain.ranks.ObserveFriendUids
import com.ivandsky.puzzlebox.domain.ranks.ObserveFriendsOverall
import com.ivandsky.puzzlebox.domain.ranks.ObserveOverall
import com.ivandsky.puzzlebox.domain.ranks.ObserveRankWindow
import com.ivandsky.puzzlebox.domain.ranks.SubmitDailyReplay
import java.time.Instant
import java.util.UUID
import kotlin.random.Random
import org.koin.dsl.module

/**
 * Use-case wiring: stateless factories over :core:data's repositories and the app's
 * [com.ivandsky.puzzlebox.game.GameRegistry], except [SyncTodayPuzzles] — a single, so
 * every surface reads the same failed fetches. Today's leaderboard day is the device's
 * local date, read when the board (re)starts. A blank draft's id is a random
 * UUID and its timestamp the wall clock. A duel's draw shuffles with [Random.Default].
 */
val domainModule = module {
    includes(insightsDomainModule)
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
    factory { ObserveFriendUids(matches = get(), identity = get()) }
    factory { ObserveRankWindow(identity = get(), leaderboard = get(), finalBoards = get(), registry = get(), calendar = get()) }
    factory { ObserveOverall(identity = get(), completions = get()) }
    factory { ObserveFriendBoards(observeFriendUids = get(), identity = get(), linkedIn = get()) }
    factory { ObserveFriendsOverall(observeRankWindow = get(), friendBoards = get(), observeOverall = get()) }
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
    factory { CreateMatch(repo = get(), local = get(), draw = get(), identity = get(), random = Random.Default, validateExact = get()) }
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
