package com.ivandsky.puzzlebox.app.widget

import com.ivandsky.puzzlebox.app.DrainTestApplication
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Robolectric only for org.json, which the JVM's android.jar stubs out.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = DrainTestApplication::class)
class WidgetSnapshotJsonTest {

    private val day = LocalDate.parse("2026-11-21")

    private fun roundTrip(state: WidgetState) = WidgetSnapshotJson.decode(WidgetSnapshotJson.encode(WidgetSnapshot(day, state)))

    @Test
    fun `every sample comes back as it was written`() {
        listOf(
            WidgetSamples.default,
            WidgetSamples.mixed,
            WidgetSamples.longest,
            WidgetSamples.offline,
            WidgetSamples.signedOut,
            WidgetSamples.rankMissing,
        ).forEach { assertEquals(WidgetSnapshot(day, it), roundTrip(it)) }
    }

    @Test
    fun `no rollover, no freeze count and nothing up next come back as nulls and Unknown`() {
        val state = WidgetSamples.default.copy(
            nextRollover = null,
            streak = WidgetStreak(days = 0, best = 3, freezesHeld = null),
            upNext = WidgetUpNext.Unknown,
        )

        assertEquals(WidgetSnapshot(day, state), roundTrip(state))
    }

    @Test
    fun `text that is not a snapshot reads as nothing`() {
        assertNull(WidgetSnapshotJson.decode("not json"))
        assertNull(WidgetSnapshotJson.decode("{}"))
        assertNull(WidgetSnapshotJson.decode("""{"version":99}"""))
    }
}
