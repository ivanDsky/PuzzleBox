package com.ivandsky.puzzlebox.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingCommand
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.transform

/**
 * This flow with a head start for a combine: it emits the last real value it carried while
 * [account] is the account that value was read for, else [seed]; then the source's own values.
 * A screen left and re-entered (past the 5 s stop grace) keeps its last numbers until fresh
 * ones land, never flashing the seed or zeroes, and a sign-in as someone else never shows the
 * first account's. The held value lives in this flow object, in process memory only.
 */
fun <T> Flow<T>.seededOnce(seed: T, account: () -> Any?): Flow<T> {
    val source = this
    var held: Pair<Any?, T>? = null
    return flow {
        emit(held?.takeIf { it.first == account() }?.second ?: seed)
        source.collect { value ->
            held = account() to value
            emit(value)
        }
    }
}

/**
 * This policy, and a change of [account] also drops the shared value (a StateFlow would
 * otherwise replay the first account's last state to the next one) and restarts the upstream
 * if anyone is still collecting.
 */
fun SharingStarted.resettingOnAccountChange(account: Flow<Any?>): SharingStarted {
    val policy = this
    return object : SharingStarted {
        override fun command(subscriptionCount: StateFlow<Int>): Flow<SharingCommand> = merge(
            policy.command(subscriptionCount),
            account.distinctUntilChanged().drop(1).transform {
                emit(SharingCommand.STOP_AND_RESET_REPLAY_CACHE)
                if (subscriptionCount.value > 0) emit(SharingCommand.START)
            },
        )
    }
}
