package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.replay.DailyReplayRepository
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.domain.LoadCustomReplay
import com.ivandsky.puzzlebox.domain.LoadDailyReplay
import com.ivandsky.puzzlebox.domain.LoadLegReplay
import com.ivandsky.puzzlebox.domain.LoadPlayerReplay
import com.ivandsky.puzzlebox.domain.LoadSharedPuzzle
import com.ivandsky.puzzlebox.domain.LoadSolveReplay
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.testing.repository.FakeDailyReplayRepository
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeMatchRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchResultOutbox
import com.ivandsky.puzzlebox.testing.repository.FakeReceivedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository
import java.time.Instant
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher

/** The replay loader over the daily and completion fakes of a test; every other source is empty. */
internal fun loadSolveReplay(
    games: GameRegistry,
    daily: DailyRepository,
    completions: CompletionRepository,
    replays: DailyReplayRepository = FakeDailyReplayRepository(),
): LoadSolveReplay {
    val received = FakeReceivedPuzzleRepository()
    val loadShared = LoadSharedPuzzle(games, FakeSharedPuzzleRepository(), received, { Budget.Unlimited }, { Instant.EPOCH })
    return LoadSolveReplay(
        games = games,
        daily = LoadDailyReplay(completions, daily),
        custom = LoadCustomReplay(completions, received, loadShared),
        leg = LoadLegReplay(FakeMatchRepository(), FakeMatchResultOutbox(), FakeIdentityService(), daily, received, loadShared),
        player = LoadPlayerReplay(replays, daily),
        completions = completions,
        dispatchers = InlineDispatcherProvider,
    )
}

private object InlineDispatcherProvider : DispatcherProvider {
    private val inline = object : CoroutineDispatcher() {
        override fun isDispatchNeeded(context: CoroutineContext) = false
        override fun dispatch(context: CoroutineContext, block: Runnable) = block.run()
    }
    override val main: CoroutineDispatcher = inline
    override val io: CoroutineDispatcher = inline
    override val default: CoroutineDispatcher = inline
}
