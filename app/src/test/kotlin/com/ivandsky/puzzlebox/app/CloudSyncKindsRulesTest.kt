package com.ivandsky.puzzlebox.app

import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.data.cloud.SyncedKind
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
 * denied. `replays` is the one name the rules list ahead of its kind (a later ticket binds it); every other rules name
 * must be bound, and every bound name must be in the rules.
 */
@RunWith(RobolectricTestRunner::class)
// DrainTestApplication (plain Application + looper drain) for DiGraphTest's reason: the real one would startKoin on its own.
@Config(sdk = [34], application = DrainTestApplication::class)
class CloudSyncKindsRulesTest {

    @Test
    fun `the bound kind names match the rules account block`() {
        val koinApp = startKoin {
            androidContext(RuntimeEnvironment.getApplication())
            modules(
                PuzzleBoxApplication.appModules + module {
                    single<IdentityService> { FakeIdentityService() }
                    single<ReleaseFlags> { FakeReleaseFlags(ReleaseFlag.LinkedInAccount) }
                },
            )
        }
        try {
            PuzzleBoxApplication.prepareCloudSync(koinApp.koin)
            val bound = koinApp.koin.getAll<SyncedKind>().map { it.kind }
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
                inRules.filter { it !in bound && it != AHEAD_OF_ITS_KIND },
            )
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
        const val AHEAD_OF_ITS_KIND = "replays"
    }
}
