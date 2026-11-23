package com.ivandsky.puzzlebox.app.e2e

import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.app.e2e.CloudSyncWorld.Device
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.trainer.TechniqueProgress
import com.ivandsky.puzzlebox.data.trainer.TechniqueProgress.Mark
import com.ivandsky.puzzlebox.data.trainer.TrainerProgressRepository
import com.ivandsky.puzzlebox.database.PuzzleBoxDatabase
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #1635 (A9, A10): Trainer progress made on one phone shows on every phone of the account once both have synced, and a
 * player's signed-out progress folds into the account at sign-in. Two [Device]s of [CloudSyncWorld], one account, nothing
 * shared but the cloud space.
 */
@RunWith(RobolectricTestRunner::class)
// DrainTestApplication (plain Application + looper drain): the merged manifest's PuzzleBoxApplication would start its own Koin.
@Config(sdk = [34], application = DrainTestApplication::class)
class CloudSyncTrainerE2ETest {

    private val world = CloudSyncWorld()
    private val ada = Identity(uid = "uid-ada", displayName = "Ada", photoUrl = null)
    private val phone = world.device("phone")
    private val tablet = world.device("tablet")

    @After
    fun closeWorld() = world.close()

    @Test
    fun `progress made on the phone shows on the tablet after both sync`() {
        phone.shell {
            phone.signIn(ada)
            awaitCloud("the phone's start-up run") { phone.synced() }
            phone.practice(LINE_LOCK, TechniqueProgress(Mark.Practicing, 2, 1_000L))
            awaitCloud("the phone's progress uploaded") { phone.uploaded(ada.uid) && inCloud().isNotEmpty() && phone.synced() }
        }
        assertEquals(mapOf(LINE_LOCK to cloudValue("practicing", 2L, 1_000L)), inCloud())
        tablet.shell {
            tablet.signIn(ada)
            awaitCloud("the tablet's first run") { tablet.synced() && tablet.progress().isNotEmpty() }
            assertEquals(
                "the tablet shows the phone's progress",
                mapOf(TechniqueId(LINE_LOCK) to TechniqueProgress(Mark.Practicing, 2, 1_000L)),
                tablet.progress(),
            )
        }
    }

    @Test
    fun `a signed-out player's progress folds into the account at sign-in and travels with it`() {
        phone.shell {
            phone.signIn(ada)
            awaitCloud("the phone's start-up run") { phone.synced() }
            phone.practice(LINE_LOCK, TechniqueProgress(Mark.Practicing, 2, 1_000L))
            awaitCloud("the phone's progress uploaded") { phone.uploaded(ada.uid) && inCloud().isNotEmpty() && phone.synced() }
        }
        tablet.shell {
            // Nobody is signed in on the tablet: both rows belong to nobody until the account claims them.
            tablet.practice(LINE_LOCK, TechniqueProgress(Mark.Practicing, 3, 3_000L))
            tablet.practice(PAIRS, TechniqueProgress(Mark.Practicing, 1, 500L))
        }
        tablet.shell {
            tablet.signIn(ada)
            awaitCloud("the tablet's run after sign-in") {
                tablet.synced() && tablet.uploaded(ada.uid) && inCloud().keys == setOf(LINE_LOCK, PAIRS)
            }
            val merged = mapOf(
                TechniqueId(LINE_LOCK) to TechniqueProgress(Mark.Practicing, 3, 3_000L),
                TechniqueId(PAIRS) to TechniqueProgress(Mark.Practicing, 1, 500L),
            )
            assertEquals("the later answer sets the count, and the signed-out technique joins", merged, tablet.progress())
            assertEquals("one row per technique is left, none of them nobody's", 2, tablet.rows().size)
            assertTrue("no signed-out row is left", tablet.rows().none { it.ownerUid == null })
            assertEquals(
                mapOf(LINE_LOCK to cloudValue("practicing", 3L, 3_000L), PAIRS to cloudValue("practicing", 1L, 500L)),
                inCloud(),
            )
        }
        phone.shell {
            awaitCloud("the phone's run after the tablet's") { phone.synced() && phone.progress().size == 2 }
            assertEquals("the phone takes both techniques from the cloud", setOf(LINE_LOCK, PAIRS), phone.progress().keys.map { it.value }.toSet())
        }
    }

    private fun Device.practice(technique: String, progress: TechniqueProgress) = runBlocking {
        graph.koin.get<TrainerProgressRepository>().update(QUEENS, TechniqueId(technique)) { progress }
    }

    /** The progress this phone's Trainer shows for Queens. */
    private fun Device.progress(): Map<TechniqueId, TechniqueProgress> =
        runBlocking { graph.koin.get<TrainerProgressRepository>().observe(QUEENS).first() }

    private fun Device.rows() = runBlocking { graph.koin.get<PuzzleBoxDatabase>().trainerProgressDao().observe(QUEENS.value, null).first() }

    /** What the cloud holds for Queens: each technique's `mark`, `inRow` and `answeredAtMs`. */
    private fun inCloud(): Map<String, Triple<Any?, Any?, Any?>> {
        val doc = world.space.docs(ada.uid, "trainer").singleOrNull { it.id == QUEENS.value } ?: return emptyMap()
        return (doc.fields["techniques"] as Map<*, *>).entries.associate { (key, value) ->
            val technique = value as Map<*, *>
            key as String to Triple(technique["mark"], technique["inRow"], technique["answeredAtMs"])
        }
    }

    private fun cloudValue(mark: String, inRow: Long, at: Long) = Triple<Any?, Any?, Any?>(mark, inRow, at)

    private companion object {
        val QUEENS = GameId("queens")
        const val LINE_LOCK = "line-lock"
        const val PAIRS = "pairs"
    }
}
