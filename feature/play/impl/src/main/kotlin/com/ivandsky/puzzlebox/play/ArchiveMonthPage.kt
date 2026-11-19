package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.designsystem.GameAccent
import com.ivandsky.puzzlebox.play.domain.ArchiveDay
import java.time.LocalDate
import java.time.YearMonth

private const val WEEK = 7

/** What every month of the calendar shares: the [nav], who is [picked], [today], the [accent] and the disc size. */
internal data class ArchiveMonthStyle(
    val nav: ArchiveMonthNav,
    val picked: LocalDate?,
    val today: LocalDate,
    val offline: Boolean,
    val accent: GameAccent,
    val compact: Boolean,
    val reduced: Boolean,
)

/**
 * One month of the calendar: [month]'s six weeks of [ArchiveDayDisc]s ([ArchiveMonthNav.grid]'s 42 days, so every month is
 * as tall) with [days] on the ones inside the archive. Opaque and held [SelectedRingRoom] in on every side, so a grid under
 * a fading or sliding one never shows the card through it and the selected ring is not clipped by the layer around it.
 */
@Composable
internal fun ArchiveMonthPage(
    style: ArchiveMonthStyle,
    month: YearMonth,
    days: List<ArchiveDay>,
    onPick: (LocalDate) -> Unit,
) {
    val disc = archiveDiscFor(style.compact)
    val cell = archiveCellFor(disc)
    val byDate = remember(days) { days.associateBy { it.date } }
    Column(Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(SelectedRingRoom)) {
        style.nav.grid(month).chunked(WEEK).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    val inMonth = YearMonth.from(date) == month
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        ArchiveDayDisc(
                            date = date,
                            day = byDate[date]?.takeIf { inMonth && style.nav.contains(date) },
                            today = date == style.today,
                            selected = inMonth && date == (style.picked ?: style.today),
                            offline = style.offline,
                            accent = style.accent,
                            cell = cell,
                            disc = disc,
                            reduced = style.reduced,
                            onClick = { onPick(date) },
                        )
                    }
                }
            }
        }
    }
}
