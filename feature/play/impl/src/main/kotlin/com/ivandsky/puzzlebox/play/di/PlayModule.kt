package com.ivandsky.puzzlebox.play.di

import com.ivandsky.puzzlebox.play.PlaySessionStore
import com.ivandsky.puzzlebox.play.WriteLane
import com.ivandsky.puzzlebox.play.domain.DeleteReceivedPuzzle
import com.ivandsky.puzzlebox.play.domain.FinishLeg
import com.ivandsky.puzzlebox.play.domain.ListCommunityPuzzles
import com.ivandsky.puzzlebox.play.domain.LoadPastDay
import com.ivandsky.puzzlebox.play.domain.ObserveFriendPuzzles
import com.ivandsky.puzzlebox.play.domain.ObserveLegTarget
import com.ivandsky.puzzlebox.play.domain.ObservePlayStats
import com.ivandsky.puzzlebox.play.domain.ObserveResultInsights
import com.ivandsky.puzzlebox.play.domain.ReportPuzzle
import com.ivandsky.puzzlebox.play.domain.linkedin.ObserveLinkedInPost
import com.ivandsky.puzzlebox.play.domain.linkedin.SubmitLinkedInResult
import com.ivandsky.puzzlebox.play.domain.nextthing.ObserveResultNextThing
import com.ivandsky.puzzlebox.play.domain.random.LoadRandomDay
import com.ivandsky.puzzlebox.play.domain.random.LoadRandomPuzzle
import com.ivandsky.puzzlebox.play.domain.random.LoadRandomShared
import kotlin.random.Random
import org.koin.dsl.module

/**
 * Play feature wiring: the per-feature modules it includes, each in its
 * own file so a new shelf, use case or view model adds a file here, not an edit to this one. The
 * [com.ivandsky.puzzlebox.game.GameRegistry] and the
 * [com.ivandsky.puzzlebox.gameui.GamePlayRegistry] come from the app's aggregation
 * (ADR-0001: one registration per game); the repositories (the tutorial-seen preferences
 * included) and the calendar from :core:data's dataModule, and the shared use cases (today's
 * set, history, stats, the rollover) from :core:domain's domainModule. The result's insights
 * ([ObserveResultInsights]) and next thing ([ObserveResultNextThing]), both Play's own, are
 * defined in [resultModule], as :core:domain reads no flag (ADR-0022). The tablet stats panel's identity and
 * leaderboard come from the backend
 * module. This module names no game; friend puzzles' wiring is [friendModule]. From friends'
 * [ObserveFriendPuzzles] and [DeleteReceivedPuzzle] are in [receivedPuzzlesModule], as are Play's own use cases: the
 * archive month and progress and a past day's [LoadPastDay] ([archiveModule]), the tablet stats panel's
 * [ObservePlayStats] ([libraryModule]), a duel leg's [FinishLeg] and [ObserveLegTarget] ([matchLegModule]),
 * Report's [ReportPuzzle] and the community list ([ListCommunityPuzzles], [communityListModule]), the result's LinkedIn
 * post ([ObserveLinkedInPost], [SubmitLinkedInResult], [linkedInModule]), and the random pick ([LoadRandomDay],
 * [LoadRandomShared], [LoadRandomPuzzle], [randomModule]), which shuffles with [Random.Default]. The live play's and
 * the custom game's view models are [gameModule]; the replay's and the compare's are [replayModule].
 *
 * [PlaySessionStore] is a single ([playSessionModule]) so live plays outlive their entries' compositions. Its
 * slot opening and every slot's writer share ONE [WriteLane], so a released slot's final
 * save is on disk before the same slot's reopen reads it; the writers' scope is process
 * lifetime, so a release never cancels a queued save.
 */
val playModule = module {
    includes(
        friendModule,
        playSessionModule,
        receivedPuzzlesModule,
        archiveModule,
        linkedInModule,
        communityListModule,
        randomModule,
        libraryModule,
        gameModule,
        resultModule,
        replayModule,
        matchLegModule,
    )
}
