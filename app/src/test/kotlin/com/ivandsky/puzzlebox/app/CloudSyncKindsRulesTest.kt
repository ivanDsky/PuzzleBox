package com.ivandsky.puzzlebox.app

import com.ivandsky.puzzlebox.data.cloud.SyncedKinds
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * ADR-0029 §5: the rules accept an account's cloud doc only for the kinds in `isSpaceKind()` of
 * `backend/firebase/firestore.rules`, so a kind bound in the sync graph without its rules entry would see every write
 * denied. `replays` is the name the rules list ahead of its kind: a later ticket binds it. Every other rules name,
 * `trainer` included (#1635, always bound), must be bound, and every bound name must be in the rules.
 */
@RunWith(RobolectricTestRunner::class)
// DrainTestApplication (plain Application + looper drain) for DiGraphTest's reason: the real one would startKoin on its own.
@Config(sdk = [34], application = DrainTestApplication::class)
class CloudSyncKindsRulesTest {

    @Test
    fun `the bound kind names match the rules account block`() {
        withBoundKinds { bound ->
            val inRules = rulesKinds()
            assertEquals("a kind is bound twice", bound.distinct(), bound)
            assertEquals(
                "kinds bound without a firestore.rules entry (every write would be denied)",
                emptyList<String>(),
                bound.filter { it !in inRules },
            )
            assertEquals(
                "kinds in firestore.rules that no SyncedKind is bound for",
                emptyList<String>(),
                inRules.filter { it !in bound && it !in AHEAD_OF_THEIR_KIND },
            )
            assertTrue("the trainer kind is not bound", "trainer" in bound)
            assertTrue("the trainer kind is not in the rules", "trainer" in inRules)
        }
    }

    /** Boots the whole app graph and hands [check] the kind names the sync engine would visit. */
    private fun withBoundKinds(check: (bound: List<String>) -> Unit) {
        val koinApp = startKoin {
            androidContext(RuntimeEnvironment.getApplication())
            modules(
                PuzzleBoxApplication.appModules + module {
                    single<IdentityService> { FakeIdentityService() }
                    single<ReleaseFlags> { FakeReleaseFlags() }
                },
            )
        }
        try {
            PuzzleBoxApplication.prepareCloudSync(koinApp.koin)
            check(koinApp.koin.get<SyncedKinds>().all.map { it.kind })
        } finally {
            stopKoin() // close() alone leaves the JVM-global context registered for later tests
        }
    }

    private fun rulesKinds(): List<String> {
        val rules = File("../backend/firebase/firestore.rules").readText()
        val body = Regex("""function\s+isSpaceKind\(\)\s*\{\s*return\s+kind\s+in\s*\[([^\]]*)]""")
            .find(rules)
            ?.groupValues?.get(1)
            ?: error("firestore.rules has no isSpaceKind() kind list")
        return Regex("""'([^']+)'""").findAll(body).map { it.groupValues[1] }.toList()
    }

    private companion object {
        val AHEAD_OF_THEIR_KIND = setOf("replays")
    }
}
