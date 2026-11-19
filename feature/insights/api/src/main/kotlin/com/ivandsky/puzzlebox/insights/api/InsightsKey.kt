package com.ivandsky.puzzlebox.insights.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The Insights hub: the months and years that have a recap, and the all-games stats row. It carries no data. Serial
 * name follows the `app.nav` convention (ADR-0008 §5).
 */
@Serializable
@SerialName("com.ivandsky.puzzlebox.app.nav.Insights")
data object InsightsKey : NavKey
