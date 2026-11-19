package com.ivandsky.puzzlebox.history.di

import com.ivandsky.puzzlebox.common.di.flagSet
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.history.domain.ranks.GetPlayerActions
import com.ivandsky.puzzlebox.history.domain.ranks.ObserveOverall
import com.ivandsky.puzzlebox.history.domain.ranks.ObserveRankWindow
import com.ivandsky.puzzlebox.history.domain.ranks.ObserveWindowRefresh
import com.ivandsky.puzzlebox.history.flags.HistoryFlag
import com.ivandsky.puzzlebox.history.ranks.PlayerSheetViewModel
import com.ivandsky.puzzlebox.history.ranks.RanksSelection
import com.ivandsky.puzzlebox.history.ranks.RanksViewModel
import com.ivandsky.puzzlebox.history.ranks.ReplayOptInViewModel
import com.ivandsky.puzzlebox.history.ranks.rankBoards
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * History feature wiring (the Ranks hub). The hub's own use cases are defined here: its boards' window
 * ([ObserveRankWindow]) and that window's refresh ([ObserveWindowRefresh]), the Overall board ([ObserveOverall]) and
 * the player sheet's actions ([GetPlayerActions]). The other use cases come from :core:domain's domainModule, the
 * [com.ivandsky.puzzlebox.game.GameRegistry] and the game brands ([com.ivandsky.puzzlebox.gameui.GamePlayRegistry])
 * from the app's aggregation (ADR-0001); [com.ivandsky.puzzlebox.data.cloud.CloudSync], which says whether history
 * is still being restored, comes from the data graph. The Ranks hub's [RanksViewModel] and its
 * [PlayerSheetViewModel] and [ReplayOptInViewModel] are bound here, and the feature's release flags ([HistoryFlag])
 * are declared here.
 */
val historyModule = module {
    flagSet("history", HistoryFlag.entries)

    factory {
        ObserveRankWindow(identity = get(), leaderboard = get(), finalBoards = get(), registry = get(), calendar = get())
    }
    factory { ObserveWindowRefresh(network = get()) }
    factory { ObserveOverall(identity = get(), completions = get()) }
    factory { GetPlayerActions(visibility = get(), completions = get()) }

    // The LinkedIn flag and where the hub opens arrive as parameters, so a graph without LinkedIn bindings builds the hub with it off (ghosts read nothing).
    viewModel { params ->
        val opening = (params.getOrNull<RanksSelection>() ?: RanksSelection()).landing(rankBoards(get<GameRegistry>()), get<GameCalendar>()::today)
        RanksViewModel(
            get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(),
            linkedIn = if (params.getOrNull<Boolean>() == true) get() else null,
            releaseFlags = get(),
            savedState = get(),
            opening = opening,
        )
    }
    viewModel { PlayerSheetViewModel(get(), get(), get(), get()) }
    viewModel { ReplayOptInViewModel(get(), releaseFlags = get()) }
}
