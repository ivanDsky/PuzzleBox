package com.ivandsky.puzzlebox.domain.di

import com.ivandsky.puzzlebox.data.match.MatchLocalRepository
import com.ivandsky.puzzlebox.domain.GetAppUpdate
import com.ivandsky.puzzlebox.domain.ComputeMatchLegs
import com.ivandsky.puzzlebox.domain.ImportDraftFromText
import com.ivandsky.puzzlebox.domain.ComputeStreak
import com.ivandsky.puzzlebox.domain.CreateDraft
import com.ivandsky.puzzlebox.domain.CreateDraftCopy
import com.ivandsky.puzzlebox.domain.CreateMatch
import com.ivandsky.puzzlebox.domain.FinishLeg
import com.ivandsky.puzzlebox.domain.GetFeaturedGame
import com.ivandsky.puzzlebox.domain.GetNextRollover
import com.ivandsky.puzzlebox.domain.LoadPastDay
import com.ivandsky.puzzlebox.domain.ObserveArchiveMonth
import com.ivandsky.puzzlebox.domain.ObserveArchiveProgress
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.ObserveGameStats
import com.ivandsky.puzzlebox.domain.ObserveLatestSolvedToday
import com.ivandsky.puzzlebox.domain.ObserveLeaderboard
import com.ivandsky.puzzlebox.domain.ObserveMatch
import com.ivandsky.puzzlebox.domain.ObserveMyDuels
import com.ivandsky.puzzlebox.domain.ObservePlayStats
import com.ivandsky.puzzlebox.domain.ObserveResultInsights
import com.ivandsky.puzzlebox.domain.ObserveResultRank
import com.ivandsky.puzzlebox.domain.ObserveTodaySet
import com.ivandsky.puzzlebox.domain.GetPuzzleDay
import com.ivandsky.puzzlebox.domain.ValidatePublish
import com.ivandsky.puzzlebox.domain.SyncTodayPuzzles
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
    factory {
        ObserveResultInsights(
            registry = get(),
            history = get(),
            computeStreak = get(),
            puzzleDay = get(),
        )
    }
    factory { LoadPastDay(daily = get(), registry = get()) }
    factory { ObserveArchiveMonth(daily = get(), saved = get(), completions = get(), calendar = get()) }
    factory { ObserveArchiveProgress(daily = get(), calendar = get(), observeGameHistory = get()) }
    factory { GetAppUpdate(releases = get(), build = get()) }
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
    factory { ObserveLatestSolvedToday(completions = get(), calendar = get(), registry = get()) }
    factory { ObserveResultRank(leaderboard = get()) }
    factory {
        CreateDraft(
            registry = get(),
            drafts = get(),
            newId = { UUID.randomUUID().toString() },
            clock = { Instant.now() },
        )
    }
    factory { CreateDraftCopy(drafts = get(), newId = { UUID.randomUUID().toString() }, clock = { Instant.now() }) }
    factory { FinishLeg(outbox = get(), identity = get(), clock = { Instant.now() }) }
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
    factory {
        ObserveMyDuels(repo = get(), outbox = get(), local = get(), identity = get(), clock = { Instant.now() })
    }
    factory { CreateMatch(repo = get(), local = get(), draw = get(), identity = get(), random = Random.Default) }
    factory { ImportDraftFromText(registry = get(), createDraft = get(), drafts = get(), dispatchers = get()) }
    factory { ValidatePublish() }
    factory {
        ObservePlayStats(
            todaySet = get(),
            stats = get(),
            history = get(),
            calendar = get(),
            leaderboard = get(),
        )
    }
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
}
