package com.ivandsky.puzzlebox.data.cloud.kinds

import android.util.Log
import com.ivandsky.puzzlebox.data.cloud.OversizedDrafts
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.database.DraftDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

private const val TAG = "OversizedDrafts"

/**
 * [OversizedDrafts] over the drafts table: the signed-in uid's rows whose blob is over [MAX_BLOB_BYTES], the size
 * [DraftsKind] never uploads. The count is logged when it changes, and nothing but the number.
 */
internal class RoomOversizedDrafts(
    private val drafts: DraftDao,
    private val identity: IdentityService,
) : OversizedDrafts {

    @OptIn(ExperimentalCoroutinesApi::class)
    override val count: Flow<Int> = identity.current
        .map { it?.uid }
        .distinctUntilChanged()
        .flatMapLatest { uid -> if (uid == null) flowOf(0) else drafts.observeOversizedCount(uid, MAX_BLOB_BYTES) }
        .distinctUntilChanged()
        .onEach { Log.i(TAG, "drafts over the sync size cap: $it") }
}
