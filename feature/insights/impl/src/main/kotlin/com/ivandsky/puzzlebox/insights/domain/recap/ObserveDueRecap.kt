package com.ivandsky.puzzlebox.insights.domain.recap

import com.ivandsky.puzzlebox.insights.label
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The recaps due on Today now (A2, A3, A4) in the one shape :app needs, so the recap period types stay inside the
 * feature: the year just ended first (Jan 1–7), then the month just ended (days 1–3), none while nothing is due. The
 * dismissal and the release flags are applied by the caller, which shows the first that is not dismissed.
 */
class ObserveDueRecap internal constructor(private val availability: ObserveRecapAvailability) {

    operator fun invoke(): Flow<List<DueRecap>> =
        availability.due().map { periods ->
            periods.map { DueRecap(periodId = it.id, label = it.label(), yearly = it is RecapPeriod.Year) }
        }
}
