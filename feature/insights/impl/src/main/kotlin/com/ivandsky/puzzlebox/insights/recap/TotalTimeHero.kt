package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.ivandsky.puzzlebox.designsystem.tabular

/** The hero's time: hours and minutes, always both ("0h 1m", "3h 47m", "99h 59m"). Seconds floor away; the average tile keeps them. */
internal fun formatHeroDuration(ms: Long): String {
    val minutes = ms.coerceAtLeast(0L) / MsPerMinute
    return "${minutes / MinutesPerHour}h ${minutes % MinutesPerHour}m"
}

/** A day's or week's time in the readout: "16m" under an hour, "1h 49m" from one; under a minute it reads "<1m", never "0m". */
internal fun formatReadoutDuration(ms: Long): String {
    val minutes = ms.coerceAtLeast(0L) / MsPerMinute
    return when {
        minutes >= MinutesPerHour -> formatHeroDuration(ms)
        minutes == 0L -> "<1m"
        else -> "${minutes}m"
    }
}

/** What a screen reader says for the hero, once: "Time spent solving, 3 hours 47 minutes". */
internal fun heroDescription(ms: Long): String {
    val minutes = ms.coerceAtLeast(0L) / MsPerMinute
    val hours = minutes / MinutesPerHour
    val rest = minutes % MinutesPerHour
    return "Time spent solving, ${hours.plural("hour")} ${rest.plural("minute")}"
}

private fun Long.plural(unit: String) = if (this == 1L) "1 $unit" else "$this ${unit}s"

/**
 * The climbing hero: [totalMs] times [progress] as plain tabular text ("3h 47m", the units set smaller), so it climbs in
 * step with the river's wave. Its size comes from the settled total, so it never jumps while it climbs, and drops a
 * step once the total has more than three digits. A screen reader reads the settled time, never a step of the climb.
 */
@Composable
internal fun TotalTimeHero(totalMs: Long, progress: () -> Float, wide: Boolean, modifier: Modifier = Modifier) {
    val long = formatHeroDuration(totalMs).count { it.isDigit() } > ShortDigits
    val size = when {
        wide && long -> WideLong
        wide -> WideShort
        long -> NarrowLong
        else -> NarrowShort
    }
    val base = MaterialTheme.typography.displayLarge.tabular().copy(fontSize = size, lineHeight = size)
    val unit = SpanStyle(fontSize = size * UnitScale)
    val shown = formatHeroDuration((totalMs * progress().coerceIn(0f, 1f)).toLong())
    Text(
        text = buildAnnotatedString {
            for (ch in shown) if (ch.isDigit() || ch == ' ') append(ch) else withStyle(unit) { append(ch) }
        },
        modifier = modifier.semantics { contentDescription = heroDescription(totalMs) },
        style = base,
        maxLines = 1,
        softWrap = false,
    )
}

private const val MsPerMinute = 60_000L
private const val MinutesPerHour = 60L
private const val ShortDigits = 3
private const val UnitScale = 0.4f
private val NarrowShort: TextUnit = 72.sp
private val NarrowLong: TextUnit = 56.sp
private val WideShort: TextUnit = 96.sp
private val WideLong: TextUnit = 80.sp
