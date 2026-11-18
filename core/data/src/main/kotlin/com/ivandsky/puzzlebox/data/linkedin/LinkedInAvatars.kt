package com.ivandsky.puzzlebox.data.linkedin

import com.ivandsky.puzzlebox.game.linkedin.LinkedInBoard
import com.ivandsky.puzzlebox.game.linkedin.LinkedInPhotoSource
import com.ivandsky.puzzlebox.model.GameId
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
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
 * downloaded after a board read, and only for a member whose file is missing: LinkedIn's photo
 * address expires, so none is kept on disk, and a photo that could not be fetched is asked for once more
 * while its day is shown ([reaskWhileShown]), then again the next time a board names the member. The file
 * name is a hash of the member, so no name or id is on disk.
 */
internal class LinkedInAvatars(
    private val dir: File,
    private val source: LinkedInPhotoSource,
    private val io: CoroutineDispatcher,
    private val scope: CoroutineScope,
    private val reaskWait: suspend () -> Unit = { delay(REASK_DELAY_MS) },
) {
    /** Bumped on every change to the directory, so [present] lists it again. */
    private val changes = MutableStateFlow(0)

    /**
     * Held while the directory is written or wiped, so a wipe cannot interleave with a write. Only ever taken
     * inside a hop to [io]: a holder that releases on the caller's thread would make a wipe called from that
     * thread wait for a thread it is itself blocking.
     */
    private val lock = Mutex()

    /** Counts wipes: a download that began before one must not write after it. Read and written under [lock]. */
    private var generation = 0

    private val permits = Semaphore(PARALLEL_DOWNLOADS)

    /** The days on screen, by [dayKey]; a day that is not on screen has no entry, so nothing is held for it. */
    private val shown = MutableStateFlow<Map<String, Shown>>(emptyMap())

    /** A day on screen: how many callers of [reaskWhileShown] show it, and its visit's first board read. */
    private data class Shown(val watchers: Int, val pass: Pass? = null)

    /** Photo addresses of a board read, in memory only; [asked] once the visit's re-ask has been claimed. */
    private data class Pass(
        val rows: List<LinkedInBoard.Row>,
        val started: Int,
        val firstPass: CompletableDeferred<Unit>,
        val asked: Boolean = false,
    )

    /**
     * The photos being fetched, as the wipe count they began under and the file name. Keyed by the count
     * too, so a wipe does not leave the member unfetchable behind a download that will drop its photo.
     */
    private val inFlight: MutableSet<Pair<Int, String>> = ConcurrentHashMap.newKeySet()

    /** The file names in the directory; collecting it lists the directory off the caller's thread. */
    val present: Flow<Set<String>> = changes.map { names() }.flowOn(io).distinctUntilChanged()

    /** The absolute path of [memberRef]'s photo when [present] names its file, else null. */
    fun pathOf(memberRef: String, present: Set<String>): String? {
        val name = fileName(memberRef)
        return if (name in present) File(dir, name).absolutePath else null
    }

    /** The wipe count now; a download started with it drops its photos if a wipe has happened since. */
    suspend fun generation(): Int = withContext(io) { lock.withLock { generation } }

    /**
     * Downloads the photos of [rows] in this store's own scope and returns at once: they must not hold a
     * sync back, and leaving the day that asked for them must not cancel them, since a past board is never
     * read again and its photos would never arrive. [started] is the [generation] read before anything a
     * wipe could have cleared was stored, so a wipe in between still drops these photos. While the day is
     * on screen ([reaskWhileShown]) and its visit holds no board read yet, the addresses are kept in memory
     * for the visit's one re-ask; a day nobody watches keeps nothing.
     */
    fun downloadInBackground(rows: List<LinkedInBoard.Row>, started: Int, game: GameId, day: LocalDate) {
        val firstPass = CompletableDeferred<Unit>()
        val key = dayKey(game, day)
        val offered = rows.filter { it.pictureUrl != null }
        shown.update { all ->
            val visit = all[key]
            when {
                visit == null || visit.pass != null -> all
                else -> all + (key to visit.copy(pass = Pass(offered, started, firstPass)))
            }
        }
        scope.launch {
            try {
                download(rows, started)
            } finally {
                firstPass.complete(Unit)
            }
        }
    }

    /**
     * Runs for as long as the caller does, i.e. while [day] is on screen, and never returns. The first
     * board read of that visit is asked for once more, after [reaskWait], for the photos still missing,
     * with the addresses it held; later board reads of the same visit and further callers for the same
     * day add no ask. Cancelling ends the visit when the last caller goes: nothing is asked after that and
     * the addresses are dropped, as they are after the ask. The same GET, size cap and permits as the first
     * pass; a photo that fails again stays missing until a later board names the member.
     */
    suspend fun reaskWhileShown(game: GameId, day: LocalDate): Nothing {
        val key = dayKey(game, day)
        shown.update { all -> all + (key to (all[key]?.let { it.copy(watchers = it.watchers + 1) } ?: Shown(1))) }
        try {
            askOnce(key)
            awaitCancellation()
        } finally {
            shown.update { all ->
                val visit = all[key] ?: return@update all
                if (visit.watchers <= 1) all - key else all + (key to visit.copy(watchers = visit.watchers - 1))
            }
        }
    }

    private suspend fun askOnce(key: String) {
        shown.first { it[key]?.pass?.asked == false }
        var claimed: Pass? = null
        shown.update { all ->
            val visit = all[key]
            val pass = visit?.pass
            if (visit == null || pass == null || pass.asked) {
                claimed = null
                all
            } else {
                claimed = pass
                all + (key to visit.copy(pass = pass.copy(rows = emptyList(), asked = true)))
            }
        }
        val pass = claimed ?: return
        pass.firstPass.await()
        reaskWait()
        download(pass.rows, pass.started)
    }

    /** The photo addresses in memory now, for a test of what is held and when it is dropped. */
    internal fun heldAddresses(): Int = shown.value.values.sumOf { it.pass?.rows?.size ?: 0 }

    private fun dayKey(game: GameId, day: LocalDate) = "${game.value}/$day"

    /** [download] of [rows] against the wipe count as it is now. */
    suspend fun download(rows: List<LinkedInBoard.Row>) = download(rows, generation())

    /**
     * Downloads the photo of each of [rows] that offers one and has no file yet: one GET each, never
     * retried, a failure leaving the member without a photo. A few run at once, so a full board does not
     * take fifty round trips in a row. A member another download is already fetching is left to it, so
     * two boards read close together do not both ask for the same photo.
     */
    private suspend fun download(rows: List<LinkedInBoard.Row>, started: Int) {
        coroutineScope {
            rows.forEach { row ->
                val url = row.pictureUrl ?: return@forEach
                launch { fetchOnce(started, row.memberRef, url) }
            }
        }
    }

    /**
     * Fetches and stores one photo unless its file exists or another download of the same member, begun
     * after the same wipe, holds it. The claim is dropped on success, failure and cancellation alike, so a
     * member that failed is asked for again; the file is written before the claim is dropped, so a download
     * that comes right after sees it. A download begun after a wipe never yields to one the wipe made stale,
     * which drops its photo.
     */
    private suspend fun fetchOnce(started: Int, memberRef: String, url: String) {
        val name = fileName(memberRef)
        val claim = started to name
        if (!inFlight.add(claim)) return
        try {
            if (withContext(io) { File(dir, name).exists() }) return
            val photo = permits.withPermit { source.fetch(url) }
            if (photo != null && photo.isNotEmpty()) save(started, name, photo)
        } finally {
            inFlight.remove(claim)
        }
    }

    /**
     * Deletes every photo and forgets every held address. A download still on the wire finds the wipe and
     * drops its photo.
     */
    suspend fun clear() {
        withContext(io) {
            lock.withLock {
                generation++
                dir.deleteRecursively()
            }
        }
        shown.update { all -> all.mapValues { (_, visit) -> visit.copy(pass = null) } }
        changes.update { it + 1 }
    }

    private suspend fun save(started: Int, name: String, photo: ByteArray) {
        val saved = withContext(io) {
            lock.withLock { started == generation && writeAtomically(File(dir, name), photo) }
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
        const val REASK_DELAY_MS = 5_000L
    }
}
