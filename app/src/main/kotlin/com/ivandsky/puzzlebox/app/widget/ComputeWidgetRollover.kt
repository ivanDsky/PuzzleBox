package com.ivandsky.puzzlebox.app.widget

import java.time.Instant
import java.time.ZoneId

/**
 * When the widget next changes day: the earliest next midnight across the listed games' [zones], strictly after `now`
 * (exactly at a midnight gives the following one). Each zone's midnight follows that zone's own rules, so a DST day
 * is not 24 hours. Separate from `GetNextRollover`, which feeds Today's countdown.
 */
class ComputeWidgetRollover {

    /** [zones] must not be empty. */
    operator fun invoke(now: Instant, zones: Collection<ZoneId>): Instant {
        require(zones.isNotEmpty()) { "no zones to roll over" }
        return zones.minOf { zone ->
            now.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()
        }
    }
}
