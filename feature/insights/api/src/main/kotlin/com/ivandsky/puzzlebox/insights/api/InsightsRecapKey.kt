package com.ivandsky.puzzlebox.insights.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A recap story. [period] is the calendar period's id: `YYYY-MM` for a month, `YYYY` for a year. Facts are recomputed
 * from the id after process death, never saved. Serial name follows the `app.nav` convention (ADR-0008 §5).
 */
@Serializable
@SerialName("com.ivandsky.puzzlebox.app.nav.InsightsRecap")
data class InsightsRecapKey(val period: String) : NavKey
