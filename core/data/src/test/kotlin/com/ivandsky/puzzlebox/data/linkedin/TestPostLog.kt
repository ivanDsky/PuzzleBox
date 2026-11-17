package com.ivandsky.puzzlebox.data.linkedin

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.ivandsky.puzzlebox.datastore.LinkedInLinkDataSource
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** A link file in a fresh temp directory, with nothing linked and nothing posted. */
internal fun testLinkData(): LinkedInLinkDataSource = LinkedInLinkDataSource(
    PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + SupervisorJob())) {
        File(createTempDirectory("linkedin_link").toFile(), "linkedin_link.preferences_pb")
    },
)

/** The post log over [data]: an empty one unless the test posts into it. */
internal fun testPostLog(data: LinkedInLinkDataSource = testLinkData()): LinkedInPostLog = LinkedInPostLog(data)
