package com.ivandsky.puzzlebox.backend.firebase

import com.google.firebase.auth.FirebaseUser
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication
import org.koin.dsl.module

/**
 * Sign-out leaves a pending clear for the next start only with [ReleaseFlag.CloudSync] on, through the real backend
 * module binding; the start-up step then consumes it once. Flag off, sign-out writes nothing.
 */
class SignOutCacheClearTest {

    private class QuietAuthGateway : AuthGateway {
        var signOuts = 0

        override fun authState(): Flow<FirebaseUser?> = emptyFlow()

        override suspend fun signInWithGoogleIdToken(idToken: String): FirebaseUser = error("not used")

        override fun signOut() {
            signOuts++
        }
    }

    private val mark = FakeCacheClearMark()
    private val gateway = QuietAuthGateway()

    private var app: KoinApplication? = null

    // The backend module is one shared object: closing the graph drops its singletons, so the next test builds its own.
    @After
    fun closeGraph() {
        app?.close()
    }

    private fun graph(flags: ReleaseFlags): KoinApplication = koinApplication {
        modules(
            firebaseBackendModule,
            module {
                single<ReleaseFlags> { flags }
                single<CoroutineScope> { CoroutineScope(Job()) }
                single<AuthGateway> { gateway }
                single<CacheClearMark> { mark }
            },
        )
    }.also { app = it }

    @Test
    fun `sign-out with the flag on leaves a pending clear and the next start consumes it once`() {
        val koin = graph(FakeReleaseFlags(ReleaseFlag.CloudSync)).koin
        val clearer = FakeFirestoreCacheClearer()

        runBlocking { koin.get<IdentityService>().signOut() }

        assertEquals(1, gateway.signOuts)
        assertTrue(mark.pending)
        val startup = PendingCacheClear(mark, clearer)
        runBlocking { startup.run() }
        runBlocking { startup.run() }
        assertEquals(1, clearer.clears)
        assertFalse(mark.pending)
    }

    @Test
    fun `sign-out with the flag off writes nothing`() {
        val koin = graph(FakeReleaseFlags()).koin

        runBlocking { koin.get<IdentityService>().signOut() }

        assertEquals(1, gateway.signOuts)
        assertEquals(0, mark.writes)
        assertFalse(mark.pending)
    }
}
