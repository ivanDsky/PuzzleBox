package com.ivandsky.puzzlebox.app.di

import android.net.ConnectivityManager
import com.ivandsky.puzzlebox.app.buildTypeChecksForUpdates
import com.ivandsky.puzzlebox.app.buildTypeUnlistedGames
import com.ivandsky.puzzlebox.app.insights.RankHistoryStarter
import com.ivandsky.puzzlebox.app.leaderboard.CustomBoardSubmitter
import com.ivandsky.puzzlebox.app.leaderboard.DailyReplayPublisher
import com.ivandsky.puzzlebox.app.leaderboard.LeaderboardSubmitWatcher
import com.ivandsky.puzzlebox.app.match.AndroidNetworkStatus
import com.ivandsky.puzzlebox.common.di.flagSet
import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.common.flags.DeclaredFlags
import com.ivandsky.puzzlebox.common.flags.FlagSet
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.common.flags.isEnabled
import com.ivandsky.puzzlebox.domain.AppBuild
import com.ivandsky.puzzlebox.domain.insights.rankhistory.SyncRankHistory
import com.ivandsky.puzzlebox.domain.ranks.SubmitDailyReplay
import com.ivandsky.puzzlebox.domain.linkedin.ObserveLinkedInDay
import com.ivandsky.puzzlebox.domain.ranks.LoadFriendsPastPage
import com.ivandsky.puzzlebox.domain.ranks.ObserveFriendDay
import com.ivandsky.puzzlebox.domain.ranks.ObserveFriendUids
import com.ivandsky.puzzlebox.gameui.flags.GameUiFlag
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.cloud.kinds.TrainerProgressKind
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRepository
import com.ivandsky.puzzlebox.data.leaderboard.SubmitStatusTracker
import com.ivandsky.puzzlebox.data.network.NetworkStatus
import com.ivandsky.puzzlebox.data.repository.CustomBoardRepository
import com.ivandsky.puzzlebox.data.repository.ReceivedPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.GameSettingsRepository
import com.ivandsky.puzzlebox.data.repository.UserPreferencesRepository
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.editor.GameEditorRegistry
import com.ivandsky.puzzlebox.gameui.editor.GameEditorUi
import com.ivandsky.puzzlebox.gameui.trainer.TrainerGame
import com.ivandsky.puzzlebox.gameui.trainer.TrainerRegistry
import com.ivandsky.puzzlebox.games.queensui.QueensSettings
import com.ivandsky.puzzlebox.games.sudokuui.SudokuHighlightSettings
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.navigation.FirstScreenReady
import com.ivandsky.puzzlebox.profile.settings.GameOptions
import com.ivandsky.puzzlebox.data.visibility.SkippedPosts
import com.ivandsky.puzzlebox.data.visibility.VisibilityRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import org.koin.dsl.onClose

/** App-process wiring: the application scope and the platform seams. */
val appModule = module {
    // Process-long scope for fire-and-forget work (leaderboard submissions, the
    // identity StateFlow): SupervisorJob so one failed child never kills the scope,
    // default dispatcher from the provider — same posture as the DataStore scope.
    // Cancelled with the graph, so an eager collector never outlives Koin.
    single<CoroutineScope> {
        CoroutineScope(SupervisorJob() + get<DispatcherProvider>().default)
    } onClose { it?.cancel() }
    // Home or the shell flips it; the splash policy reads it.
    single { FirstScreenReady() }
    // The watcher's per-post outcome: one instance, written by the watcher and read by
    // the Result board and the tablet panel.
    single { SubmitStatusTracker() }
    single {
        LeaderboardSubmitWatcher(
            records = get<CompletionRepository>(),
            identityService = get<IdentityService>(),
            leaderboard = get<LeaderboardRepository>(),
            calendar = get<GameCalendar>(),
            scope = get<CoroutineScope>(),
            statuses = get<SubmitStatusTracker>(),
            visibility = get<VisibilityRepository>(),
            skipped = get<SkippedPosts>(),
        )
    }
    single { DailyReplayPublisher(publish = get<SubmitDailyReplay>(), scope = get<CoroutineScope>()) }
    // The weekly rank history filler (Insights): started only while the stats page or the year recap is on.
    single {
        RankHistoryStarter(flags = get<ReleaseFlags>(), fill = get<SyncRankHistory>()::invoke, scope = get<CoroutineScope>())
    }
    // The ranks outside the hub and Result's Friends board also count the friends a full global page leaves out.
    factory { LoadFriendsPastPage(get<LeaderboardRepository>()) }
    // The ranks outside the hub (Home, Result, the play summary) follow its Friends/Global setting.
    factory {
        ObserveFriendDay.Binding(
            ObserveFriendDay(
                preferences = get<UserPreferencesRepository>(),
                friendUids = get<ObserveFriendUids>(),
                linkedInDay = get<ObserveLinkedInDay>(),
                completions = get<CompletionRepository>(),
                friendsPast = get<LoadFriendsPastPage>(),
            ),
        )
    }
    // Friend puzzle wins post to their own boards, never through the daily watcher.
    single {
        CustomBoardSubmitter(
            identity = get<IdentityService>(),
            received = get<ReceivedPuzzleRepository>(),
            completions = get<CompletionRepository>(),
            boards = get<CustomBoardRepository>(),
            scope = get<CoroutineScope>(),
        )
    }
    // The one Android-specific seam of the duel result poster, which core:data wires.
    single<NetworkStatus> { AndroidNetworkStatus(androidContext().getSystemService(ConnectivityManager::class.java)) }

    // Game aggregation (ADR-0001): the composition root owns the seams no game module
    // may know about. The registry is the one lookup for consumers that must not know
    // game ids (Home, History, play shell, data decode). Adding a game means one module
    // line in [PuzzleBoxApplication.appModules] (its plugin + its GamePlayUi) plus its
    // place in [GAME_ORDER]. Koin's getAll order is not a product order, so the
    // registry is sorted here: Home's Today card and the play picker follow it.
    single {
        GameRegistry(getAll<GamePlugin<*, *, *>>().sortedWith(gameOrder), buildTypeUnlistedGames)
            .also { requireEveryGamePlayable(it, get()) }
    }

    // Every game module's play registration; a duplicate game id throws (ADR-0007).
    single { GamePlayRegistry(getAll<GamePlayUi>()) }

    // Every game module's editor UI (ADR-0019); a duplicate game id throws.
    single { GameEditorRegistry(getAll<GameEditorUi>()) }

    // Every game's Trainer adapter, bound inside a TrainerGame.Binding that holds it only while that game's flag is on
    // (a definition cannot be left out by a flag). A factory, so each resolve reads
    // the flags afresh. Empty until a game's flag is on, and the Train tab shows only while it is not.
    factory { TrainerRegistry(getAll<TrainerGame.Binding>().mapNotNull { it.game }, TRAINER_ORDER) }

    // The trainer progress kind joins the cloud sync only while a Train flag is on (the same Binding shape; :core:data
    // cannot read the game UI flags). cloudSyncModule's SyncedKinds reads this once per process, when the sync graph is
    // built, so a flag flipped in the debug Settings reaches the sync on the next start, unlike the registry above.
    factory {
        val flags = get<ReleaseFlags>()
        val trainerOn = TRAIN_FLAGS.any { flags.isEnabled(it) }
        TrainerProgressKind.Binding(if (trainerOn) get<TrainerProgressKind>() else null)
    }

    // Every module's release flags, ordered by owner (ADR-0022). A duplicate key throws on first resolve; a repeated
    // owner is caught by konsist P15 (Koin keeps the last set).
    single { DeclaredFlags(getAll<FlagSet>()) }
    // Declared in core:game-ui, which has no Koin, so registered here.
    flagSet("game-ui", GameUiFlag.entries)

    // The installed build GetAppUpdate compares against; debug never checks.
    single {
        val context = androidContext()
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        AppBuild(versionName = packageInfo.versionName.orEmpty(), checksForUpdates = buildTypeChecksForUpdates)
    }
    // Sudoku's two tint toggles live in the persisted preferences; its UI module only sees this seam.
    single<SudokuHighlightSettings> { PreferencesSudokuHighlightSettings(get<GameSettingsRepository>()) }
    // Queens' Auto-cross lives in the persisted game settings; its UI module only sees this seam.
    single<QueensSettings> { PreferencesQueensSettings(get<GameSettingsRepository>(), get<CoroutineScope>()) }
    // Settings' Games switches share those stored values, so a flip there shows in the board's ⋮ menu and back.
    single<GameOptions> { PreferencesGameOptions(get<GameSettingsRepository>()) }
}

/** ADR-0008 "Adding a game": a plugin bound without its GamePlayUi is a wiring bug —
 *  fail at the registry's first resolve instead of silently leaving the picker. */
internal fun requireEveryGamePlayable(games: GameRegistry, play: GamePlayRegistry) {
    for (plugin in games.all + games.unlisted) {
        val id = plugin.definition.id
        require(play[id] != null) { "game '${id.value}' has no GamePlayUi registered (missing its -ui module)" }
    }
}

/** Product order of the shipped games: the four LinkedIn games together, then Strands, then the debug-only Sudoku. */
private val GAME_ORDER = listOf("zip", "tango", "queens", "minisudoku", "strands", "sudoku")

/** The Train tab's game order: Queens, Tango, then Mini Sudoku. A game with no adapter bound is skipped. */
private val TRAINER_ORDER = listOf(GameId("queens"), GameId("tango"), GameId("minisudoku"))

/** The flags that put a game in the Train tab; the trainer progress syncs while any of them is on. */
private val TRAIN_FLAGS = listOf(GameUiFlag.TrainQueens, GameUiFlag.TrainTango, GameUiFlag.TrainSudoku)

/** [GAME_ORDER] rank; an unlisted game sorts after every listed one, then by id. */
private val gameOrder: Comparator<GamePlugin<*, *, *>> =
    compareBy<GamePlugin<*, *, *>> { plugin ->
        GAME_ORDER.indexOf(plugin.definition.id.value).takeIf { it >= 0 } ?: GAME_ORDER.size
    }.thenBy { it.definition.id.value }
