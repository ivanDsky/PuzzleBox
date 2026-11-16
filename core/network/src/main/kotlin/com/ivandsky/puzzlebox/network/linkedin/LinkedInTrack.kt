package com.ivandsky.puzzlebox.network.linkedin

import java.time.Instant
import java.time.ZoneId
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The `x-li-track` header of LinkedIn's desktop web client: a constant client description (the version the rest
 * of the wire is pinned to, `web`, a desktop form factor, display numbers) plus the device's timezone. Only the
 * timezone varies; the display values are the capture's, not the phone's, since the request claims to be the web page.
 */
internal object LinkedInTrack {

    fun header(zone: ZoneId, now: Instant): String {
        val offsetHours = zone.rules.getOffset(now).totalSeconds / SECONDS_PER_HOUR
        val track: JsonObject = buildJsonObject {
            put("clientVersion", VERSION)
            put("mpVersion", VERSION)
            put("osName", "web")
            put("timezoneOffset", hours(offsetHours))
            put("timezone", zone.id)
            put("deviceFormFactor", "DESKTOP")
            put("mpName", "web")
            put("displayDensity", 2)
            put("displayWidth", 2880)
            put("displayHeight", 1800)
        }
        return track.toString()
    }

    /** A whole-hour offset is an integer, a half-hour zone keeps its fraction. */
    private fun hours(offset: Double): JsonElement =
        JsonPrimitive(if (offset % 1.0 == 0.0) offset.toInt() else offset)

    private const val VERSION = "0.2.8598"
    private const val SECONDS_PER_HOUR = 3600.0
}
