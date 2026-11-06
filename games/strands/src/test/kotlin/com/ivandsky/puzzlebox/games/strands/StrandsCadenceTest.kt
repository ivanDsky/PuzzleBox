package com.ivandsky.puzzlebox.games.strands

import com.ivandsky.puzzlebox.game.Cadence
import com.ivandsky.puzzlebox.game.today
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class StrandsCadenceTest {

    @Test
    fun `a non-LA device zone still yields the LA day`() {
        val saved = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
            val la = ZoneId.of("America/Los_Angeles")
            assertEquals(Cadence.Daily(la), StrandsDefinition.cadence)
            assertEquals(LocalDate.now(la), StrandsDefinition.cadence.today())
        } finally {
            TimeZone.setDefault(saved)
        }
    }
}
