package com.ivandsky.puzzlebox.app.di

import com.ivandsky.puzzlebox.app.buildTypeChecksForUpdates
import com.ivandsky.puzzlebox.app.buildTypeUnlistedGames
import com.ivandsky.puzzlebox.app.leaderboard.LeaderboardSubmitWatcher
import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.domain.AppBuild
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRepository
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.editor.GameEditorRegistry
import com.ivandsky.puzzlebox.gameui.editor.GameEditorUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** App-process wiring: the application scope and the platform seams. */
val appModule = module {
    // Process-long scope for fire-and-forget work (leaderboard submissions, the
    // identity StateFlow): SupervisorJob so one failed child never kills the scope,
    // default dispatcher from the provider — same posture as the DataStore scope.
    single<CoroutineScope> {
        CoroutineScope(SupervisorJob() + get<DispatcherProvider>().default)
    }
    single {
        LeaderboardSubmitWatcher(
            records = get<CompletionRepository>(),
            identityService = get<IdentityService>(),
            leaderboard = get<LeaderboardRepository>(),
            scope = get<CoroutineScope>(),
        )
    }

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
    // The installed build GetAppUpdate compares against; debug never checks (spec A4).
    single {
        val context = androidContext()
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        AppBuild(versionName = packageInfo.versionName.orEmpty(), checksForUpdates = buildTypeChecksForUpdates)
    }
}

/** ADR-0008 "Adding a game": a plugin bound without its GamePlayUi is a wiring bug —
 *  fail at the registry's first resolve instead of silently leaving the picker. */
internal fun requireEveryGamePlayable(games: GameRegistry, play: GamePlayRegistry) {
    for (plugin in games.all + games.unlisted) {
        val id = plugin.definition.id
        require(play[id] != null) { "game '${id.value}' has no GamePlayUi registered (missing its -ui module)" }
    }
}

/** Product order of the shipped games: Home's Today card and the picker list them in this order. */
private val GAME_ORDER = listOf("zip", "tango", "queens", "strands", "minisudoku", "sudoku")

/** [GAME_ORDER] rank; an unlisted game sorts after every listed one, then by id. */
private val gameOrder: Comparator<GamePlugin<*, *, *>> =
    compareBy<GamePlugin<*, *, *>> { plugin ->
        GAME_ORDER.indexOf(plugin.definition.id.value).takeIf { it >= 0 } ?: GAME_ORDER.size
    }.thenBy { it.definition.id.value }
