package com.ivandsky.puzzlebox.insights.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The stats page. [gameId] is a [com.ivandsky.puzzlebox.model.GameId] value; null opens the all-games page. Serial
 * name follows the `app.nav` convention (ADR-0008 §5).
 */
@Serializable
@SerialName("com.ivandsky.puzzlebox.app.nav.InsightsStats")
data class InsightsStatsKey(val gameId: String? = null) : NavKey
