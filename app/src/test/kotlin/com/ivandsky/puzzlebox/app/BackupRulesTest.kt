package com.ivandsky.puzzlebox.app

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

/**
 * What leaves the device in a cloud backup or a device transfer: the web view's data
 * directory (the LinkedIn session cookie lives there), the LinkedIn and Final board databases with
 * their write-ahead-log side files, the link-state file, the cloud-sync state file and the
 * Firestore SDK's disk cache (it holds connections' names) with its side files. Backup rules exclude whole files,
 * so each of the thirteen is pinned by name; the rest of the app's data keeps backing up.
 */
@RunWith(RobolectricTestRunner::class)
// DrainTestApplication (plain Application + looper drain) for DiGraphTest's reason: the real one would startKoin on its own.
@Config(sdk = [34], application = DrainTestApplication::class)
class BackupRulesTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `cloud backup excludes the web view, the LinkedIn and Final board databases, the link state, the sync state and the Firestore cache`() {
        assertEquals(EXPECTED, excludesIn(R.xml.data_extraction_rules, "cloud-backup"))
    }

    @Test
    fun `device transfer excludes the web view, the LinkedIn and Final board databases, the link state, the sync state and the Firestore cache`() {
        assertEquals(EXPECTED, excludesIn(R.xml.data_extraction_rules, "device-transfer"))
    }

    @Test
    fun `full backup content on API 26 to 30 excludes the same thirteen`() {
        assertEquals(EXPECTED, excludesIn(R.xml.backup_rules, "full-backup-content"))
    }

    @Test
    fun `the main database is never excluded`() {
        for ((xml, section) in listOf(R.xml.backup_rules to "full-backup-content") +
            listOf("cloud-backup", "device-transfer").map { R.xml.data_extraction_rules to it }) {
            assertTrue(excludesIn(xml, section).none { it.startsWith("database/puzzlebox") })
        }
    }

    @Test
    fun `the rest of the app still backs up`() {
        val flags = context.applicationInfo.flags
        assertTrue(
            "allowBackup was switched off; the rules must exclude files instead",
            flags and ApplicationInfo.FLAG_ALLOW_BACKUP != 0,
        )
    }

    /** The `domain/path` of every `<exclude>` directly under the section [section], in order. */
    private fun excludesIn(xmlRes: Int, section: String): List<String> {
        val parser = context.resources.getXml(xmlRes)
        val found = mutableListOf<String>()
        var inSection = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when {
                    parser.name == section -> inSection = true
                    inSection && parser.name == "exclude" ->
                        found += "${parser.getAttributeValue(null, "domain")}/" +
                            parser.getAttributeValue(null, "path")
                    inSection -> error("unexpected <${parser.name}> in <$section>")
                }
                XmlPullParser.END_TAG -> if (parser.name == section) inSection = false
            }
            event = parser.next()
        }
        return found
    }

    private companion object {
        // SQLitePersistence.databaseName for the default app, google-services project and database.
        const val FIRESTORE_CACHE = "firestore.%5BDEFAULT%5D.puzzlebox-f3356.%28default%29"

        val EXPECTED = listOf(
            "root/app_webview",
            "database/linkedin.db",
            "database/linkedin.db-wal",
            "database/linkedin.db-shm",
            "database/final_boards.db",
            "database/final_boards.db-wal",
            "database/final_boards.db-shm",
            "file/linkedin_link.preferences_pb",
            "file/cloud_sync_state.preferences_pb",
            "database/$FIRESTORE_CACHE",
            "database/$FIRESTORE_CACHE-wal",
            "database/$FIRESTORE_CACHE-shm",
            "database/$FIRESTORE_CACHE-journal",
        )
    }
}
