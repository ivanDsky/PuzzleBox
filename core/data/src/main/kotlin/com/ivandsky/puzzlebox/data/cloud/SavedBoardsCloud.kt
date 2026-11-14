package com.ivandsky.puzzlebox.data.cloud

/**
 * Deletes saved boards here and in the cloud. `linkedInCloudSyncModule` binds it, and the LinkedIn row's Delete saved
 * boards resolves it.
 */
interface SavedBoardsCloud {
    /** Deletes this device's saved boards and the account's cloud boards. */
    suspend fun deleteEverywhere(): CloudResult<Unit>
}
