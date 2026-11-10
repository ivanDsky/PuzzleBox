package com.ivandsky.puzzlebox.compete.duel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val ENDS_PATTERN = "EEE HH:mm"

/** The one "ends" text every duel surface shows: weekday and time of [deadline] in [zone], spelled in [locale] ("Sat 18:00"). */
internal fun formatEnds(deadline: Instant, zone: ZoneId, locale: Locale): String =
    DateTimeFormatter.ofPattern(ENDS_PATTERN, locale).format(deadline.atZone(zone))

/** The clock compete screens read. Tests provide a fixed one; the default is the device's clock and its current zone. */
internal val LocalCompeteClock = compositionLocalOf<Clock> { DeviceClock }

/** Reads the device zone on every call, so a timezone change while the app is alive shows at once (a `systemDefaultZone()` clock pins the zone at creation). */
private object DeviceClock : Clock() {
    override fun getZone(): ZoneId = ZoneId.systemDefault()

    override fun withZone(zone: ZoneId): Clock = system(zone)

    override fun instant(): Instant = Instant.now()
}

/** What a screen needs to write a time: when it is, in which zone, in which locale. Read once per screen with [rememberCompeteTime]. */
@Immutable
internal class CompeteTime(val clock: Clock, val locale: Locale) {
    val zone: ZoneId get() = clock.zone

    fun now(): Instant = clock.instant()

    fun ends(deadline: Instant): String = formatEnds(deadline, zone, locale)
}

/** The screen's [CompeteTime]: the provided [LocalCompeteClock] and the locale the configuration currently asks for. */
@Composable
internal fun rememberCompeteTime(): CompeteTime {
    val clock = LocalCompeteClock.current
    val locale = LocalConfiguration.current.locales[0]
    return remember(clock, locale) { CompeteTime(clock, locale) }
}
