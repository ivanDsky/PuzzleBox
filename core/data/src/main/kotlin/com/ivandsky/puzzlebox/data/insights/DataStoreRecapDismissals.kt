package com.ivandsky.puzzlebox.data.insights

import com.ivandsky.puzzlebox.datastore.PuzzleBoxPreferencesDataSource
import kotlinx.coroutines.flow.Flow

/** [RecapDismissals] in the app's Preferences DataStore file, beside the other device-local choices. */
internal class DataStoreRecapDismissals(
    private val dataSource: PuzzleBoxPreferencesDataSource,
) : RecapDismissals {

    override fun observe(): Flow<Set<String>> = dataSource.recapsDismissed

    override suspend fun dismiss(periodId: String) {
        dataSource.dismissRecap(periodId)
    }
}
