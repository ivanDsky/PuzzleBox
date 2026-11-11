package com.ivandsky.puzzlebox.data.cloud

/**
 * Deletes saved boards here and in the cloud. Resolved with `getOrNull<SavedBoardsCloud>()` by the LinkedIn row; null
 * (flag off) means today's UI exactly.
 */
interface SavedBoardsCloud {
    /** Deletes this device's saved boards and the account's cloud boards. */
    suspend fun deleteEverywhere(): CloudResult<Unit>
}
