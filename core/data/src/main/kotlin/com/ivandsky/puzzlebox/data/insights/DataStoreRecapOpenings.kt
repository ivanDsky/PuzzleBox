package com.ivandsky.puzzlebox.data.insights

import com.ivandsky.puzzlebox.datastore.PuzzleBoxPreferencesDataSource
import kotlinx.coroutines.flow.Flow

/** [RecapOpenings] in the app's Preferences DataStore file, beside the other device-local choices. */
internal class DataStoreRecapOpenings(
    private val dataSource: PuzzleBoxPreferencesDataSource,
) : RecapOpenings {

    override fun observe(): Flow<Set<String>> = dataSource.recapsOpened

    override suspend fun markOpened(periodId: String) {
        dataSource.markRecapOpened(periodId)
    }
}
