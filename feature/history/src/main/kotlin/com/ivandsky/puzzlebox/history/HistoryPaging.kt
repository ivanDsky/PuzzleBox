package com.ivandsky.puzzlebox.history

import androidx.paging.InvalidatingPagingSourceFactory
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.ivandsky.puzzlebox.data.repository.ZipDailyRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Page shape: one 20-row load with a 5-row lookahead — laziness visible past 30 days. */
internal const val PAGE_SIZE = 20
private const val PREFETCH_DISTANCE = 5

/**
 * Lazy paging over the captured days (A8: "paginated lazily"), newest first per the
 * repository contract. The repository stays exactly the KDoc contract ticket 14
 * consumes (list flow, no paging types); this source pages a snapshot of it, and each
 * new history emission invalidates the current generation, so a freshly captured day
 * appears without rebuilding the screen.
 */
fun historyDays(
    daily: ZipDailyRepository,
    scope: CoroutineScope,
): Flow<PagingData<HistoryDayRow>> {
    val latestRows = daily.observeHistory()
        .map { entries -> entries.map { it.toRow() } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())
    val factory = InvalidatingPagingSourceFactory { HistoryPagingSource(latestRows.value) }
    scope.launch {
        latestRows.collect { factory.invalidate() }
    }
    return Pager(
        config = PagingConfig(
            pageSize = PAGE_SIZE,
            initialLoadSize = PAGE_SIZE,
            prefetchDistance = PREFETCH_DISTANCE,
            enablePlaceholders = false,
        ),
        pagingSourceFactory = factory,
    ).flow
}

/** Pages one snapshot of the captured days; replaced wholesale on invalidation. */
internal class HistoryPagingSource(
    private val days: List<HistoryDayRow>,
) : PagingSource<Int, HistoryDayRow>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, HistoryDayRow> {
        val start = when (params) {
            is LoadParams.Refresh -> params.key ?: 0
            is LoadParams.Append -> params.key ?: 0
            is LoadParams.Prepend -> params.key ?: return emptyBefore(params)
        }.coerceIn(0, days.size)
        val end = (start + params.loadSize).coerceAtMost(days.size)
        return LoadResult.Page(
            data = days.subList(start, end),
            prevKey = start.takeIf { it > 0 },
            nextKey = end.takeIf { it < days.size },
            itemsBefore = start,
            itemsAfter = days.size - end,
        )
    }

    /** [LoadParams.Prepend] without a key: nothing exists before the start. */
    private fun emptyBefore(params: LoadParams<Int>): LoadResult.Page<Int, HistoryDayRow> =
        LoadResult.Page(data = emptyList(), prevKey = null, nextKey = null)

    override fun getRefreshKey(state: PagingState<Int, HistoryDayRow>): Int? =
        state.anchorPosition?.coerceIn(0, days.size)
}
