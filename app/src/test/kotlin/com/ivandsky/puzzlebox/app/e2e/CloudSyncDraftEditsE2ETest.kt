package com.ivandsky.puzzlebox.app.e2e

import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.data.identity.Identity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A draft that keeps changing while it is pending still reaches the cloud (A12, A20, review V-2), over the real flag-on
 * graph in [CloudSyncWorld]. The author edits during an upload and again during the engine's re-run, then stops: the
 * draft's id was pending the whole time, so only a content change can start the run that sends the last edit.
 */
@RunWith(RobolectricTestRunner::class)
// DrainTestApplication (plain Application + looper drain): the merged manifest's PuzzleBoxApplication would start its own Koin.
@Config(sdk = [34], application = DrainTestApplication::class)
class CloudSyncDraftEditsE2ETest {

    private val world = CloudSyncWorld()
    private val ada = Identity(uid = "uid-ada", displayName = "Ada", photoUrl = null)

    @After
    fun closeWorld() = world.close()

    private fun titleInCloud(): String? = world.space.docs(ada.uid, "drafts").singleOrNull()?.fields?.get("name") as? String

    @Test
    fun `a draft edited during an upload and during its re-run still ends in the cloud as the last edit`() {
        val phone = world.device()
        phone.shell {
            phone.signIn(ada)
            phone.makeDraft("draft-1", "v1")
            awaitCloud("v1 in the cloud") { titleInCloud() == "v1" && phone.uploaded(ada.uid) }

            val upload = world.gate.holdNextPut()
            phone.makeDraft("draft-1", "v2")
            awaitCloud("v2's upload in flight") { upload.arrived.isCompleted }

            // Edited during the upload; the re-run the engine makes for what is left pending is held in turn.
            val rerun = world.gate.holdNextPut()
            phone.makeDraft("draft-1", "v3")
            upload.release.complete(Unit)
            awaitCloud("the re-run in flight") { rerun.arrived.isCompleted }

            // Edited during the re-run too, after which nothing but this edit can start another run.
            phone.makeDraft("draft-1", "v4")
            rerun.release.complete(Unit)

            awaitCloud("the last edit in the cloud") { titleInCloud() == "v4" && phone.uploaded(ada.uid) }
            assertEquals("v4", titleInCloud())
        }
    }
}
