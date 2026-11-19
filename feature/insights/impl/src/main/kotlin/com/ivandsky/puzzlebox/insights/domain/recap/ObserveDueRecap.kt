package com.ivandsky.puzzlebox.insights.domain.recap

import com.ivandsky.puzzlebox.insights.label
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The recap due on Today now (A2, A4) in the one shape :app needs, so the recap period types stay inside the feature.
 * Null while none is due; the dismissal and the release flag are applied by the caller.
 */
class ObserveDueRecap internal constructor(private val availability: ObserveRecapAvailability) {

    operator fun invoke(): Flow<DueRecap?> =
        availability.due().map { period -> period?.let { DueRecap(periodId = it.id, label = it.label()) } }
}
