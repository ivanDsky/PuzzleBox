package com.ivandsky.puzzlebox.testing.repository

import com.ivandsky.puzzlebox.data.match.LegResult
import com.ivandsky.puzzlebox.data.match.MatchResultOutbox
import com.ivandsky.puzzlebox.data.match.OutboxResult
import com.ivandsky.puzzlebox.data.match.ResultPostState
import com.ivandsky.puzzlebox.model.ShareCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** In-memory [MatchResultOutbox]; it passes the same contract test as the Room adapter. */
class FakeMatchResultOutbox : MatchResultOutbox {

    private data class Key(val code: ShareCode, val legIndex: Int, val uid: String)

    private val store = MutableStateFlow<Map<Key, OutboxResult>>(emptyMap())

    override suspend fun enqueue(code: ShareCode, result: LegResult) {
        val key = Key(code, result.legIndex, result.uid)
        store.update { rows ->
            if (key in rows) rows else rows + (key to OutboxResult(code, result, ResultPostState.Pending, 0))
        }
    }

    override fun observe(code: ShareCode): Flow<List<OutboxResult>> = rows { all ->
        all.filter { it.code == code }.sortedWith(compareBy({ it.result.legIndex }, { it.result.uid }))
    }

    override fun observePending(): Flow<List<OutboxResult>> = rows { all ->
        all.filter { it.state == ResultPostState.Pending }
            .sortedWith(compareBy({ it.result.completedAtMs }, { it.code.value }, { it.result.legIndex }, { it.result.uid }))
    }

    override fun observeAll(): Flow<List<OutboxResult>> = rows { all ->
        all.sortedWith(
            compareByDescending<OutboxResult> { it.result.completedAtMs }
                .thenBy { it.code.value }.thenBy { it.result.legIndex }.thenBy { it.result.uid },
        )
    }

    override suspend fun update(
        code: ShareCode,
        legIndex: Int,
        uid: String,
        state: ResultPostState,
        attempts: Int,
    ) {
        val key = Key(code, legIndex, uid)
        store.update { rows ->
            rows[key]?.let { rows + (key to it.copy(state = state, attempts = attempts)) } ?: rows
        }
    }

    private fun rows(select: (List<OutboxResult>) -> List<OutboxResult>): Flow<List<OutboxResult>> =
        store.map { select(it.values.toList()) }.distinctUntilChanged()
}
