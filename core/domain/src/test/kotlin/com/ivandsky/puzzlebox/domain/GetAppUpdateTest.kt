package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.update.AppRelease
import com.ivandsky.puzzlebox.testing.repository.FakeAppReleaseRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Decision table: an update only when checks are on, both versions parse, and latest > installed. */
class GetAppUpdateTest {

    private val url = "https://github.com/ivandsky/PuzzleBox/releases/tag/v1.3.0"

    private fun check(tag: String?, installed: String = "1.2.0", checks: Boolean = true): Pair<AppUpdate?, Int> {
        val releases = FakeAppReleaseRepository(release = tag?.let { AppRelease(tag = it, url = url) })
        var result: AppUpdate? = null
        runTest { result = GetAppUpdate(releases, AppBuild(installed, checks))() }
        return result to releases.calls
    }

    @Test
    fun newerReleaseIsAnUpdateWithItsUrl() {
        assertEquals(AppUpdate(AppVersion(1, 3, 0), url), check("v1.3.0").first)
    }

    @Test
    fun equalReleaseIsNoUpdate() = assertNull(check("v1.2.0").first)

    @Test
    fun olderReleaseIsNoUpdate() = assertNull(check("v1.1.9").first)

    @Test
    fun noReleaseIsNoUpdate() = assertNull(check(null).first)

    @Test
    fun unparsableTagIsNoUpdate() = assertNull(check("nightly").first)

    @Test
    fun unparsableInstalledVersionIsNoUpdate() = assertNull(check("v1.3.0", installed = "dev").first)

    @Test
    fun checksOffIsNoUpdateAndSkipsTheRepository() {
        val (update, calls) = check("v1.3.0", checks = false)
        assertNull(update)
        assertEquals(0, calls)
    }
}
