package com.ivandsky.puzzlebox.backend.firebase

import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.ivandsky.puzzlebox.data.visibility.VisibilityTier
import android.os.Looper
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The daily replay doc store against the Firestore emulator under the deployed `firestore.rules`: a wrong collection
 * path or field name passes the faked [ReplayDocStore] tests and fails here. Skipped, not failed, when the emulators
 * are not up. Start them from a firebase.json that names `firestore.rules` and enables both emulators:
 * `firebase emulators:start --only firestore,auth --project demo-puzzlebox` (Firestore 8080, Auth 9099).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FirestoreReplayDocStoreEmulatorTest {

    private val store = FirestoreReplayDocStore()

    @Before
    fun freshEmulator() = EmulatorBackend.startClean()

    @Test
    fun `a replay written by its owner reads back byte for byte once the owner's tier is replays`() = EmulatorBackend.blocking {
        val uid = EmulatorBackend.signInAsNewUser()
        EmulatorBackend.setTier(uid, VisibilityTier.Replays)
        val blob = byteArrayOf(1, 2, 3, 127, -128, 0, 9)

        store.put(EmulatorBackend.dailyBoardId(), uid, encodeReplayDoc(uid, blob, createdAtMs = 1_700_000_000_000L))
        val fields = store.get(EmulatorBackend.dailyBoardId(), uid)

        assertArrayEquals(blob, decodeReplayBlob(checkNotNull(fields)))
        assertEquals(uid, fields["uid"])
        assertEquals(1L, fields["formatVersion"])
        assertEquals(1_700_000_000_000L, fields["createdAtMs"])
    }

    @Test
    fun `a reader gets null for a replay that was never written`() = EmulatorBackend.blocking {
        val owner = EmulatorBackend.signInAsNewUser()
        EmulatorBackend.setTier(owner, VisibilityTier.Replays)

        assertNull(store.get(EmulatorBackend.dailyBoardId(), owner))
    }

    @Test
    fun `the read is refused while the owner's tier is below replays`() = EmulatorBackend.blocking {
        val uid = EmulatorBackend.signInAsNewUser()
        store.put(EmulatorBackend.dailyBoardId(), uid, encodeReplayDoc(uid, byteArrayOf(5), createdAtMs = 1L))
        EmulatorBackend.setTier(uid, VisibilityTier.Duel)

        val refused = runCatching { store.get(EmulatorBackend.dailyBoardId(), uid) }.exceptionOrNull()
        assertTrue("expected a permission failure, got $refused", refused is FirebaseFirestoreException)
    }

    @Test
    fun `the owner deletes the replay and it reads as absent`() = EmulatorBackend.blocking {
        val uid = EmulatorBackend.signInAsNewUser()
        EmulatorBackend.setTier(uid, VisibilityTier.Replays)
        store.put(EmulatorBackend.dailyBoardId(), uid, encodeReplayDoc(uid, byteArrayOf(5), createdAtMs = 1L))

        store.delete(EmulatorBackend.dailyBoardId(), uid)

        assertNull(store.get(EmulatorBackend.dailyBoardId(), uid))
    }
}

/** The emulator wiring shared by the three emulator tests; the SDK singletons live for the whole test JVM. */
internal object EmulatorBackend {
    private const val HOST = "127.0.0.1"
    private const val FIRESTORE_PORT = 8080
    private const val AUTH_PORT = 9099
    private const val PROJECT = "demo-puzzlebox"
    private const val TIMEOUT_NANOS = 60_000_000_000L
    private var connected = false

    /** Skips the calling test when either emulator is down, then connects the SDK and empties Firestore. */
    fun startClean() {
        assumeTrue("Firestore and Auth emulators are not running", listening(FIRESTORE_PORT) && listening(AUTH_PORT))
        connect()
        val clear = URL("http://$HOST:$FIRESTORE_PORT/emulator/v1/projects/$PROJECT/databases/(default)/documents")
            .openConnection() as HttpURLConnection
        clear.requestMethod = "DELETE"
        check(clear.responseCode == 200) { "clearing the emulator answered ${clear.responseCode}" }
        FirebaseAuth.getInstance().signOut()
    }

    /** A fresh Auth user, signed in; the uid is the rules' `request.auth.uid`. */
    suspend fun signInAsNewUser(): String =
        checkNotNull(
            FirebaseAuth.getInstance()
                .createUserWithEmailAndPassword("${UUID.randomUUID()}@example.test", "password-123")
                .await().user,
        ).uid

    fun signOut() = FirebaseAuth.getInstance().signOut()

    /** The signed-in user's own tier doc, at the path the rules name (not the production constant, so a wrong path there is caught). */
    suspend fun setTier(uid: String, tier: VisibilityTier) {
        FirebaseFirestore.getInstance().collection("visibility").document(uid)
            .set(encodeTierDoc(tier)).await()
    }

    /** `zip_<today UTC>`: inside the rules' entry window whatever the clock says. */
    fun dailyBoardId(): String = "zip_${LocalDate.now(ZoneOffset.UTC)}"

    /**
     * Runs [body] off the test thread and pumps the main looper until it ends: Robolectric's test thread IS the main
     * thread, and the Auth SDK completes tasks on it, so a plain runBlocking would deadlock.
     */
    fun <T> blocking(body: suspend () -> T): T {
        val work = CoroutineScope(Dispatchers.IO).async { body() }
        val deadline = System.nanoTime() + TIMEOUT_NANOS
        while (!work.isCompleted) {
            check(System.nanoTime() < deadline) { "emulator call did not finish in time" }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(2)
        }
        return runBlocking { work.await() }
    }

    private fun connect() {
        if (connected) return
        val options = FirebaseOptions.Builder()
            .setProjectId(PROJECT).setApplicationId("1:1:android:1").setApiKey("fake").build()
        FirebaseApp.initializeApp(RuntimeEnvironment.getApplication(), options)
        FirebaseFirestore.getInstance().useEmulator(HOST, FIRESTORE_PORT)
        FirebaseAuth.getInstance().useEmulator(HOST, AUTH_PORT)
        connected = true
    }

    private fun listening(port: Int) = runCatching {
        Socket().use { it.connect(InetSocketAddress(HOST, port), 300) }
    }.isSuccess
}
