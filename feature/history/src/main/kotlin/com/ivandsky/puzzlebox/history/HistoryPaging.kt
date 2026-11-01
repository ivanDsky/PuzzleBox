package com.ivandsky.puzzlebox.history

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.ivandsky.puzzlebox.data.repository.ZipDailyRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/** Page shape: one 20-row load with a 5-row lookahead — laziness visible past 30 days. */
internal const val PAGE_SIZE = 20
private const val PREFETCH_DISTANCE = 5

/**
 * Lazy paging over the captured days (A8: "paginated lazily"), newest first per the
 * repository contract. The repository stays exactly the KDoc contract ticket 14
 * consumes (list flow, no paging types): each history emission switches to a Pager
 * built from that snapshot ([flatMapLatest] — a generation can never be built from a
 * pre-emission empty list), so a freshly captured day appears without rebuilding the
 * screen.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun historyDays(daily: ZipDailyRepository): Flow<PagingData<HistoryDayRow>> = daily.observeHistory()
    .map { entries -> entries.map { it.toRow() } }
    .flatMapLatest { rows ->
        Pager(
            config = PagingConfig(
                pageSize = PAGE_SIZE,
                initialLoadSize = PAGE_SIZE,
                prefetchDistance = PREFETCH_DISTANCE,
                enablePlaceholders = false,
            ),
            pagingSourceFactory = { HistoryPagingSource(rows) },
        ).flow
    }

/** Pages one snapshot of the captured days; replaced wholesale on history change. */
internal class HistoryPagingSource(
    private val days: List<HistoryDayRow>,
) : PagingSource<Int, HistoryDayRow>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, HistoryDayRow> {
        val (start, end) = when (params) {
            is LoadParams.Refresh -> (params.key ?: 0).let { it to it + params.loadSize }
            is LoadParams.Append -> (params.key ?: 0).let { it to it + params.loadSize }
            // Prepend is the BACKWARD window ending at the key — never forward rows.
            is LoadParams.Prepend ->
                params.key?.let { maxOf(0, it - params.loadSize) to it }
                    ?: return emptyBefore(params)
        }
        val from = start.coerceIn(0, days.size)
        val to = end.coerceIn(from, days.size)
        return LoadResult.Page(
            data = days.subList(from, to),
            prevKey = from.takeIf { it > 0 },
            nextKey = to.takeIf { it < days.size },
            itemsBefore = from,
            itemsAfter = days.size - to,
        )
    }

    /** [LoadParams.Prepend] without a key: nothing exists before the start. */
    private fun emptyBefore(params: LoadParams<Int>): LoadResult.Page<Int, HistoryDayRow> =
        LoadResult.Page(data = emptyList(), prevKey = null, nextKey = null)

    override fun getRefreshKey(state: PagingState<Int, HistoryDayRow>): Int? =
        state.anchorPosition?.coerceIn(0, days.size)
}
