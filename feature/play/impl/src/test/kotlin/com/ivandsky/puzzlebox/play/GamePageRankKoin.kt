package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.domain.ObserveLeaderboard
import com.ivandsky.puzzlebox.domain.ranks.ObserveFriendDay
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.play.friend.dismissedGoogleSignIn
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeLeaderboardRepository
import java.time.LocalDate
import org.koin.core.module.dsl.viewModel
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

/**
 * Starts a Koin graph that builds the game page's board ([GamePageRankViewModel]) over empty fakes for [ids].
 * Close the returned application when the test ends.
 */
internal fun startGamePageRankKoin(ids: List<GameId>, today: LocalDate = LocalDate.parse("2026-11-05")): KoinApplication {
    stopKoin() // tests share the JVM-global Koin context
    val games = FakeGames(ids).games
    val calendar = FakeGameCalendar(today)
    val identity = FakeIdentityService()
    return startKoin {
        modules(
            module {
                viewModel { (game: GameId) ->
                    GamePageRankViewModel(
                        game,
                        ObserveLeaderboard(identity, FakeLeaderboardRepository(emptyMap()), games, calendar),
                        calendar,
                        games,
                        identity,
                        dismissedGoogleSignIn,
                        linkedIn = null,
                        friendDay = null, // no Friends/Global setting
                    )
                }
            },
        )
    }
}

/** The hub-off binding a play test graph needs when it loads [playModule]: the root's answer is no friend day. */
internal val hubOffFriendDay = module {
    single { ObserveFriendDay.Binding(reader = null) } // no Friends/Global setting
}

/** The release flags a play test graph needs when it loads [playModule]: every flag off. */
internal val flagsOff = module {
    single<ReleaseFlags> { FakeReleaseFlags() }
}
