package com.ivandsky.puzzlebox.play

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.domain.moments.Moment
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.LocalSolveCelebrationOverlay
import com.ivandsky.puzzlebox.gameui.SolveCelebration
import com.ivandsky.puzzlebox.model.PlaySlot
import org.koin.compose.koinInject

/**
 * The solve celebration's hand-off from a daily board to its Result (L2). Bound once per graph: [enabled] is the
 * Result moments flag as the play module read it, so the board's route and Result never read the flag themselves.
 *
 * The board [arm]s the solve it hands off and that solve's Result [take]s it, once. A won play stays in the store and
 * a finished day opens straight on Result, so without this Result could not tell a solve just made from one opened
 * again; a Result built again (rotation, process death, a second visit) finds nothing armed and plays nothing.
 */
internal class CelebrationOnResult(val enabled: Boolean) {
    private var armed: Pair<PlaySlot, ActivePlay.Solve>? = null

    /** Marks [solve] of [slot] as just made. Does nothing when not [enabled]. Main thread only, like [take]. */
    fun arm(slot: PlaySlot, solve: ActivePlay.Solve) {
        if (enabled) armed = slot to solve
    }

    /** True once for the armed [solve] of [slot], which it then forgets; false for any other. */
    fun take(slot: PlaySlot, solve: ActivePlay.Solve): Boolean {
        val mine = armed == slot to solve
        if (mine) armed = null
        return mine
    }
}

/**
 * Wraps a daily board's [content]: when the celebration is on, the board skips its own stamp and hands off right
 * after the sweep ([LocalSolveCelebrationOverlay]), and the hand-off it is given arms [CelebrationOnResult] before
 * calling [onSolved]. Off, the board and the hand-off are exactly as without the wrapper.
 */
@Composable
internal fun CelebrationOnResultHandOff(
    onSolved: (PlaySlot, Long, Int) -> Unit,
    content: @Composable (onSolved: (PlaySlot, Long, Int) -> Unit) -> Unit,
) {
    val celebration = koinInject<CelebrationOnResult>()
    val solved by rememberUpdatedState(onSolved)
    val handOff = remember(celebration) {
        { slot: PlaySlot, timeMs: Long, hints: Int ->
            celebration.arm(slot, ActivePlay.Solve(timeMs, hints))
            solved(slot, timeMs, hints)
        }
    }
    CompositionLocalProvider(LocalSolveCelebrationOverlay provides celebration.enabled) { content(handOff) }
}

/**
 * The celebration over Result: [SolveCelebration] in [brand], longer when one of [moments] is known on the phone by
 * the end of the normal length. A friend-board moment never lengthens it: it arrives from the network, and the
 * celebration does not wait for the network.
 */
@Composable
internal fun ResultCelebration(
    brand: GameBrand,
    moments: List<Moment>,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SolveCelebration(brand = brand, moment = moments.any { it.lengthensCelebration() }, onDone = onDone, modifier = modifier)
}

private fun Moment.lengthensCelebration(): Boolean = when (this) {
    Moment.PersonalBest, is Moment.FirstSubX, Moment.FullSetDone, is Moment.Streak -> true
    is Moment.TopThreeFriends, is Moment.PassedFriend -> false
}
