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
 * directory (the LinkedIn session cookie lives there), the LinkedIn database with its
 * write-ahead-log side files, and the link-state file. Backup rules exclude whole files,
 * so each of the five is pinned by name; the rest of the app's data keeps backing up.
 */
@RunWith(RobolectricTestRunner::class)
// Plain Application for DiGraphTest's reason: the real one would startKoin on its own.
@Config(sdk = [34], application = android.app.Application::class)
class BackupRulesTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `cloud backup excludes the web view, the LinkedIn database and the link state`() {
        assertEquals(EXPECTED, excludesIn(R.xml.data_extraction_rules, "cloud-backup"))
    }

    @Test
    fun `device transfer excludes the web view, the LinkedIn database and the link state`() {
        assertEquals(EXPECTED, excludesIn(R.xml.data_extraction_rules, "device-transfer"))
    }

    @Test
    fun `full backup content on API 26 to 30 excludes the same five`() {
        assertEquals(EXPECTED, excludesIn(R.xml.backup_rules, "full-backup-content"))
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
        val EXPECTED = listOf(
            "root/app_webview",
            "database/linkedin.db",
            "database/linkedin.db-wal",
            "database/linkedin.db-shm",
            "file/linkedin_link.preferences_pb",
        )
    }
}
