package com.ivandsky.puzzlebox.app.e2e

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.navigation3.runtime.NavKey
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ivandsky.puzzlebox.app.PuzzleBoxApplication
import com.ivandsky.puzzlebox.app.nav.topLevelDestinations
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.data.cloud.CloudDoc
import com.ivandsky.puzzlebox.data.cloud.CloudResult
import com.ivandsky.puzzlebox.data.cloud.CloudSpace
import com.ivandsky.puzzlebox.data.cloud.CloudSync
import com.ivandsky.puzzlebox.data.cloud.CloudSyncRunner
import com.ivandsky.puzzlebox.data.cloud.CloudSyncState
import com.ivandsky.puzzlebox.data.cloud.OwnerScope
import com.ivandsky.puzzlebox.data.cloud.SyncedKind
import com.ivandsky.puzzlebox.data.di.dataModule
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.linkedin.LinkedInDays
import com.ivandsky.puzzlebox.data.model.Draft
import com.ivandsky.puzzlebox.data.network.NetworkStatus
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.DraftRepository
import com.ivandsky.puzzlebox.database.Migrations
import com.ivandsky.puzzlebox.database.PuzzleBoxDatabase
import com.ivandsky.puzzlebox.database.linkedin.LinkedInDatabase
import com.ivandsky.puzzlebox.database.linkedin.LinkedInMigrations
import com.ivandsky.puzzlebox.database.linkedin.SavedBoardEntity
import com.ivandsky.puzzlebox.datastore.SyncStateDataSource
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.games.zip.ZipPuzzleRef
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.testing.cloud.FakeCloudSpace
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import com.ivandsky.puzzlebox.testing.network.FakeNetworkStatus
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.room.RoomQueryThread
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.Executors
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.koin.android.ext.koin.androidContext
import org.koin.core.Koin
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.dsl.onClose

/**
 * The cloud sync test world (tickets 33, 34 and 35): the real flag-on app graph, with `ReleaseFlag.CloudSync` on (and
 * `LinkedInAccount` too when [linkedIn]), over one shared [space], one [network] and fake identities. Everything the
 * sync touches is the production code: Room, the owner scope, every kind, the engine and the runner. Only the edges are
 * fakes: the cloud space ([FakeCloudSpace] behind a [CloudGate]), the identity and the network.
 *
 * A [Device] is one phone: its own Room files and its own sync bookkeeping, so two devices on the same [space] share
 * nothing but the cloud, like two phones. Koin is process-global, so only one device runs at a time: a [Device.shell]
 * run starts it and stops it, and what it stored stays for its next run (a relaunch). [authority] is the account the
 * cloud authenticates; it follows the running device's identity, which [Device.signIn] and [Device.signOut] keep
 * together. [edges] adds a test's own bindings last in every device's graph, so they win. [close] after the run.
 */
internal class CloudSyncWorld(
    private val linkedIn: Boolean = false,
    private val edges: Module.() -> Unit = {},
) : AutoCloseable {

    /** What the cloud authenticates: the signed-in account of the device that is running. */
    val authority = FakeIdentityService()

    /** The cloud: every device syncs against this one space. */
    val space = FakeCloudSpace(authority)

    /** The space the devices see: [space], except for the call a test holds. */
    val gate = CloudGate(space)

    val network = FakeNetworkStatus()

    private val devices = mutableListOf<Device>()

    /** A new phone on this world's cloud. [name] keeps its files apart from the other devices'. */
    fun device(name: String = "phone"): Device = Device(name).also(devices::add)

    override fun close() {
        devices.forEach(Device::close)
    }

    /**
     * A [CloudSpace] that passes every call to [inner], except: while [offline] every call answers
     * [CloudResult.Offline] and reaches nothing; the next `put` can be held ([holdNextPut]) before it reaches the space,
     * or made to reach the space and then never answer ([loseNextAnswer]), as a process that dies between the put and its
     * acknowledgement.
     */
    internal class CloudGate(private val inner: CloudSpace) : CloudSpace {

        /** One held `put`: [arrived] once it is in flight, [release] to let it reach the space. */
        class Hold {
            val arrived = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
        }

        /** True while the phone has no connection to the cloud. */
        @Volatile
        var offline = false

        private val lock = Any()
        private var next: Hold? = null
        private var lose: CompletableDeferred<Unit>? = null

        /** The next `put`, whoever makes it, waits on the returned hold before it reaches the space. */
        fun holdNextPut(): Hold = Hold().also { hold -> synchronized(lock) { next = hold } }

        /** The next `put` reaches the space, then never answers; the returned deferred completes once it has. */
        fun loseNextAnswer(): CompletableDeferred<Unit> = CompletableDeferred<Unit>().also { lost -> synchronized(lock) { lose = lost } }

        override suspend fun changedSince(uid: String, kind: String, cursorMs: Long?): CloudResult<List<CloudDoc>> =
            if (offline) CloudResult.Offline else inner.changedSince(uid, kind, cursorMs)

        override suspend fun put(uid: String, kind: String, docs: List<CloudDoc>): CloudResult<Unit> {
            if (offline) return CloudResult.Offline
            val hold = synchronized(lock) { next.also { next = null } }
            if (hold != null) {
                hold.arrived.complete(Unit)
                hold.release.await()
            }
            val result = inner.put(uid, kind, docs)
            val lost = synchronized(lock) { lose.also { lose = null } }
            if (lost != null) {
                lost.complete(Unit)
                awaitCancellation()
            }
            return result
        }

        override suspend fun delete(uid: String, kind: String, ids: List<String>): CloudResult<Unit> =
            if (offline) CloudResult.Offline else inner.delete(uid, kind, ids)

        override suspend fun deleteAll(uid: String, kinds: List<String>): CloudResult<Unit> =
            if (offline) CloudResult.Offline else inner.deleteAll(uid, kinds)
    }

    /** One phone: the app graph plus the files that outlive a run of it. */
    inner class Device(private val name: String) : AutoCloseable {

        private val context: Context get() = ApplicationProvider.getApplicationContext()
        private val io = Executors.newSingleThreadExecutor()
        private val ioScope = CoroutineScope(SupervisorJob() + io.asCoroutineDispatcher())

        /** The files this phone opened, closed with it. */
        private val opened = mutableListOf<() -> Unit>()

        private val puzzleDb: PuzzleBoxDatabase by lazy {
            Room.databaseBuilder(context, PuzzleBoxDatabase::class.java, "$name-puzzlebox.db").addMigrations(*Migrations.ALL)
                .setQueryExecutor(RoomQueryThread.executor).build()
                .also { opened += it::close }
        }
        private val linkedInDb: LinkedInDatabase by lazy {
            Room.databaseBuilder(context, LinkedInDatabase::class.java, "$name-linkedin.db").addMigrations(*LinkedInMigrations.ALL)
                .setQueryExecutor(RoomQueryThread.executor).build()
                .also { opened += it::close }
        }
        private val syncState: DataStore<Preferences> by lazy {
            PreferenceDataStoreFactory.create(scope = ioScope) { File(context.filesDir, "$name-cloud_sync_state.preferences_pb") }
        }

        val graph: E2eKoinGraph = E2eKoinGraph(
            E2E_TODAY,
            overrides = module {
                single<CloudSpace> { gate }
                single<NetworkStatus> { network }
                single { puzzleDb }
                single { linkedInDb }
                single { SyncStateDataSource(syncState) }
                // The records store is the Room one the app ships. Its class is internal to core:data, so it is asked of
                // a nested data graph that shares this graph's database, registry and owner scope.
                single(named(DATA)) {
                    val outer = getKoin()
                    koinApplication {
                        androidContext(context)
                        modules(
                            dataModule,
                            module {
                                single<PuzzleBoxDatabase> { puzzleDb }
                                single<GameRegistry> { outer.get() }
                                single<OwnerScope> { outer.get() }
                            },
                        )
                    }.koin
                } onClose { it?.close() }
                single<CompletionRepository> { get<Koin>(named(DATA)).get() }
                edges()
            },
            FakeReleaseFlags(*listOfNotNull(ReleaseFlag.CloudSync, ReleaseFlag.LinkedInAccount.takeIf { linkedIn }).toTypedArray()),
        )

        /** The account that signs in here is the one the cloud authenticates. */
        fun signIn(identity: Identity) {
            authority.currentIdentity = identity
            graph.identity.currentIdentity = identity
        }

        fun signOut() {
            authority.currentIdentity = null
            graph.identity.currentIdentity = null
        }

        /**
         * Runs [block] against this phone's shell on [state]: the graph starts, the cloud sync loads and its runner
         * starts as `PuzzleBoxApplication.onCreate` does, all before the UI is drawn, so no screen ever holds the
         * flag-off owner scope. The graph stops after; the phone's files stay.
         */
        fun shell(tab: NavKey = PlayKey, block: ShellRobot.() -> Unit) {
            authority.currentIdentity = graph.identity.currentIdentity
            graph.start()
            try {
                check(PuzzleBoxApplication.loadCloudSync(graph.koin)) { "CloudSync is off" }
                graph.koin.get<CloudSyncRunner>().start()
                val robot = ShellRobot(graph, tabAt(tab), topLevelDestinations)
                val run = object : Statement() {
                    override fun evaluate() {
                        robot.launch()
                        robot.block()
                    }
                }
                robot.rule
                    .apply(run, Description.createTestDescription(ShellRobot::class.java, "cloudSyncShell"))
                    .evaluate()
            } finally {
                // The process dies with the run: its sync scope stops, so nothing of this run syncs in the next one.
                graph.koin.get<CoroutineScope>().cancel()
                graph.stop()
            }
        }

        val cloud: CloudSync get() = graph.koin.get()
        private val owner: OwnerScope get() = graph.koin.get()

        /** The rows a screen of this phone shows right now, for the account [OwnerScope] says is on screen. */
        fun shownRecords(): List<CompletionRecord> = runBlocking { graph.koin.get<CompletionRepository>().observeRecords().first() }

        fun shownDrafts(): List<Draft> = runBlocking { graph.koin.get<DraftRepository>().observeAll().first() }

        /** Which of [days] a screen of this phone shows a saved Zip board for, as [LinkedInDays.observe] reads local state. */
        fun shownBoardDays(vararg days: LocalDate): Set<LocalDate> = runBlocking {
            val linkedIn = graph.koin.get<LinkedInDays>()
            days.filterTo(HashSet()) { linkedIn.observe(ZipDefinition.id, it).first().boardFetchedAtMs != null }
        }

        /** A finished game of [day], as the play flow emits it; the repository stamps the owner and the first win. */
        fun finish(day: LocalDate, puzzleNumber: Int?, timeMs: Long = 61_000): PuzzleRef {
            val ref = ZipPuzzleRef(PuzzleKey.Daily(day, puzzleNumber))
            runBlocking {
                graph.koin.get<CompletionRepository>().onCompleted(CompletionRecord(ref, timeMs, 0, RecordStats(1, "{}")))
            }
            return ref
        }

        /** A draft of the Studio, stamped with the signed-in account by the repository. */
        fun makeDraft(id: String, title: String) {
            val draft = Draft(
                id = id,
                gameId = ZipDefinition.id,
                variant = VariantId("zip/6"),
                draftFormatVersion = 1,
                blob = title.toByteArray(),
                title = title,
                updatedAt = Instant.parse("2026-11-01T10:00:00Z"),
                testSolve = null,
                reservedCode = null,
                imported = false,
                authorName = "",
                note = "",
            )
            runBlocking { graph.koin.get<DraftRepository>().upsert(draft) }
        }

        /**
         * A saved LinkedIn board of [day], as the ghost writer saves one on a read: stamped with the signed-in
         * account (null when nobody is), with no upload acknowledged yet.
         */
        fun saveBoard(day: LocalDate) {
            runBlocking {
                linkedInDb.savedBoardDao().upsert(
                    SavedBoardEntity(
                        game = ZipDefinition.id.value,
                        day = day.toString(),
                        profileId = "ACoAAFAKEprofile000",
                        ownerUid = owner.writer(),
                        readAtMs = 1_700_000_000_000L,
                        complete = true,
                        uploadedFingerprint = null,
                    ),
                )
            }
        }

        /** True when every kind has nothing of [uid]'s left to upload. */
        fun uploaded(uid: String): Boolean = runBlocking { graph.koin.getAll<SyncedKind>().all { it.pending(uid).isEmpty() } }

        /** True when the engine's last run finished with nothing wrong. */
        fun synced(): Boolean = cloud.state.value is CloudSyncState.Synced

        override fun close() {
            ioScope.cancel()
            io.shutdown()
            opened.forEach { it() }
        }
    }

    companion object {
        const val CLOUD_TIMEOUT_MS = 30_000L
        private const val DATA = "cloud.world.data"
    }
}

/** Polls [condition] until it holds, with room for the real 2 s debounce and the real threads the sync runs on. */
internal fun ShellRobot.awaitCloud(what: String, condition: () -> Boolean) {
    try {
        rule.waitUntil(CloudSyncWorld.CLOUD_TIMEOUT_MS) { condition() }
    } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
        throw AssertionError("timed out waiting for $what", e)
    }
}
