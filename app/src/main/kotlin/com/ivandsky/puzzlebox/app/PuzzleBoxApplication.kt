package com.ivandsky.puzzlebox.app

import android.app.Application
import com.ivandsky.puzzlebox.app.di.appModule
import com.ivandsky.puzzlebox.app.di.buildTypeReleaseFlagsModule
import com.ivandsky.puzzlebox.app.di.reminderModule
import com.ivandsky.puzzlebox.app.insights.RankHistoryStarter
import com.ivandsky.puzzlebox.app.leaderboard.CustomBoardSubmitter
import com.ivandsky.puzzlebox.app.leaderboard.DailyReplayPublisher
import com.ivandsky.puzzlebox.app.leaderboard.LeaderboardSubmitWatcher
import com.ivandsky.puzzlebox.app.reminder.ReminderAppHooks
import com.ivandsky.puzzlebox.data.match.MatchResultPoster
import com.ivandsky.puzzlebox.backend.firebase.PendingCacheClear
import com.ivandsky.puzzlebox.backend.firebase.firebaseBackendModule
import com.ivandsky.puzzlebox.auth.di.authModule
import com.ivandsky.puzzlebox.common.di.commonModule
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.common.flags.isEnabled
import com.ivandsky.puzzlebox.compete.di.competeModule
import com.ivandsky.puzzlebox.create.di.createModule
import com.ivandsky.puzzlebox.data.cloud.CloudSyncRunner
import com.ivandsky.puzzlebox.data.cloud.OwnerScope
import com.ivandsky.puzzlebox.data.di.cloudSyncModule
import com.ivandsky.puzzlebox.data.di.dataModule
import com.ivandsky.puzzlebox.data.di.linkedInCloudSyncModule
import com.ivandsky.puzzlebox.domain.di.domainModule
import com.ivandsky.puzzlebox.games.queens.queensModule
import com.ivandsky.puzzlebox.games.queensui.queensUiModule
import com.ivandsky.puzzlebox.games.sudoku.miniSudokuModule
import com.ivandsky.puzzlebox.games.sudokuui.miniSudokuUiModule
import com.ivandsky.puzzlebox.games.zip.zipModule
import com.ivandsky.puzzlebox.games.zipui.zipUiModule
import com.ivandsky.puzzlebox.games.tango.tangoModule
import com.ivandsky.puzzlebox.games.tangoui.tangoUiModule
import com.ivandsky.puzzlebox.games.strands.strandsModule
import com.ivandsky.puzzlebox.games.strandsui.strandsUiModule
import com.ivandsky.puzzlebox.history.di.historyModule
import com.ivandsky.puzzlebox.home.di.homeModule
import com.ivandsky.puzzlebox.insights.di.insightsModule
import com.ivandsky.puzzlebox.play.di.playModule
import com.ivandsky.puzzlebox.profile.di.profileModule
import com.ivandsky.puzzlebox.train.di.trainModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.Koin
import org.koin.core.context.startKoin
import kotlinx.coroutines.runBlocking

class PuzzleBoxApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        val koin = startKoin {
            androidContext(this@PuzzleBoxApplication)
            modules(appModules)
        }.koin
        // A sign-out or erase since the last run left a mark: clear the Firestore disk cache now, before any store
        // touches Firestore (the SDK refuses once started). No mark, no work.
        runBlocking { koin.get<PendingCacheClear>().run() }
        // The owner scope is created before any starter, so it is the signed-in one from the first row any of them reads.
        prepareCloudSync(koin)
        // The submit hook: record writes are the only leaderboard source; async,
        // eventual, and silent when signed out or offline.
        koin.get<LeaderboardSubmitWatcher>().start()
        // Friend puzzle wins: posts pending at the last run retry on every app start.
        koin.get<CustomBoardSubmitter>().start()
        // Duel results: pending posts retry on every app start; idle while the outbox is empty.
        koin.get<MatchResultPoster>().start()
        // Daily replays: an accepted solve's replay goes public only on the Replays tier.
        koin.get<DailyReplayPublisher>().start()
        // The weekly rank history (Insights): filled only while the stats page or the year recap is on.
        koin.get<RankHistoryStarter>().start()
        // Cloud sync: the one place that starts runs besides "Sync now".
        koin.get<CloudSyncRunner>().start()
        // The daily reminder: follows the stored choice and the foreground.
        koin.get<ReminderAppHooks>().start()
    }

    companion object {
        /**
         * Readies the sync graph that [appModules] already binds: adds the LinkedIn boards kind and saved-board deletion,
         * then creates the owner scope, which records the last signed-in uid from its construction, so it must exist
         * before any starter reads a row.
         */
        fun prepareCloudSync(koin: Koin) {
            koin.loadModules(listOf(linkedInCloudSyncModule))
            koin.get<OwnerScope>()
        }

        val appModules =
            listOf(commonModule, dataModule, cloudSyncModule, firebaseBackendModule, domainModule, homeModule, historyModule, playModule, createModule, authModule(R.string.default_web_client_id), profileModule, competeModule, insightsModule, trainModule, zipModule, zipUiModule, queensModule, queensUiModule, tangoModule, tangoUiModule, strandsModule, strandsUiModule, miniSudokuModule, miniSudokuUiModule, appModule, reminderModule, buildTypeReleaseFlagsModule) + buildTypeGameModules
    }
}
