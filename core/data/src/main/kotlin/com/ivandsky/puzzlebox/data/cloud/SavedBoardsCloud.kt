package com.ivandsky.puzzlebox.data.cloud

/**
 * Deletes saved boards here and in the cloud. Bound only with `ReleaseFlag.LinkedInAccount` on, so the LinkedIn row
 * resolves it with `getOrNull<SavedBoardsCloud>()`; null means the row offers no Delete saved boards.
 */
interface SavedBoardsCloud {
    /** Deletes this device's saved boards and the account's cloud boards. */
    suspend fun deleteEverywhere(): CloudResult<Unit>
}
