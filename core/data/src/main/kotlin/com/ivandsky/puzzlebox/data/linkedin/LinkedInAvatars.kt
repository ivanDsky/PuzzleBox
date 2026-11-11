package com.ivandsky.puzzlebox.data.linkedin

import com.ivandsky.puzzlebox.game.linkedin.LinkedInBoard
import com.ivandsky.puzzlebox.game.linkedin.LinkedInPhotoSource
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * The connections' profile photos, one file per member in [dir]. The directory is meant to sit in
 * `noBackupFilesDir`, so Auto Backup and device transfer never copy a photo off the phone. A photo is
 * downloaded once, after a board read, and only for a member whose file is missing: LinkedIn's photo
 * address expires, so none is kept, and a photo that could not be fetched is simply asked for again the
 * next time a board names the member. The file name is a hash of the member, so no name or id is on disk.
 */
internal class LinkedInAvatars(
    private val dir: File,
    private val source: LinkedInPhotoSource,
    private val io: CoroutineDispatcher,
) {
    /** Bumped on every change to the directory, so [present] lists it again. */
    private val changes = MutableStateFlow(0)

    /** Held while the directory is written or wiped, so a wipe cannot interleave with a write. */
    private val lock = Mutex()

    /** Counts wipes: a download that began before one must not write after it. Read and written under [lock]. */
    private var generation = 0

    private val permits = Semaphore(PARALLEL_DOWNLOADS)

    /** The file names in the directory; collecting it lists the directory off the caller's thread. */
    val present: Flow<Set<String>> = changes.map { names() }.flowOn(io).distinctUntilChanged()

    /** The absolute path of [memberRef]'s photo when [present] names its file, else null. */
    fun pathOf(memberRef: String, present: Set<String>): String? {
        val name = fileName(memberRef)
        return if (name in present) File(dir, name).absolutePath else null
    }

    /**
     * Downloads the photo of each of [rows] that offers one and has no file yet: one GET each, never
     * retried, a failure leaving the member without a photo. A few run at once, so a full board does not
     * hold the sync for fifty round trips in a row.
     */
    suspend fun download(rows: List<LinkedInBoard.Row>) {
        val started = lock.withLock { generation }
        val wanted = withContext(io) { rows.filter { it.pictureUrl != null && !File(dir, fileName(it.memberRef)).exists() } }
        coroutineScope {
            wanted.forEach { row ->
                launch {
                    val photo = permits.withPermit { source.fetch(checkNotNull(row.pictureUrl)) }
                    if (photo != null && photo.isNotEmpty()) save(started, row.memberRef, photo)
                }
            }
        }
    }

    /** Deletes every photo. A download still on the wire finds the wipe and drops its photo. */
    suspend fun clear() {
        lock.withLock {
            generation++
            withContext(io) { dir.deleteRecursively() }
        }
        changes.update { it + 1 }
    }

    private suspend fun save(started: Int, memberRef: String, photo: ByteArray) {
        val saved = lock.withLock {
            started == generation && withContext(io) { writeAtomically(File(dir, fileName(memberRef)), photo) }
        }
        if (saved) changes.update { it + 1 }
    }

    /** A reader never sees half a photo: the bytes land in a temporary file that is renamed into place. */
    private fun writeAtomically(target: File, photo: ByteArray): Boolean {
        val temporary = File(dir, target.name + TEMPORARY)
        return try {
            dir.mkdirs()
            temporary.writeBytes(photo)
            temporary.renameTo(target).also { if (!it) temporary.delete() }
        } catch (_: IOException) {
            temporary.delete()
            false
        }
    }

    private fun names(): Set<String> = dir.list().orEmpty().filter { it.endsWith(EXTENSION) }.toSet()

    private fun fileName(memberRef: String): String =
        MessageDigest.getInstance("SHA-256").digest(memberRef.toByteArray()).joinToString("") { "%02x".format(it) } + EXTENSION

    private companion object {
        const val EXTENSION = ".jpg"
        const val TEMPORARY = ".tmp"
        const val PARALLEL_DOWNLOADS = 4
    }
}
