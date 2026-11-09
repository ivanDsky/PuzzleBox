package com.ivandsky.puzzlebox.history.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Root of the History tab: the unfiltered history browser with no back arrow. A pushed
 * [HistoryKey] keeps its arrow. Own file because one public type per file (Konsist S1);
 * registered in [historyNavKeys]. Serial name pinned (saved state).
 */
@Serializable
@SerialName("com.ivandsky.puzzlebox.app.nav.HistoryRoot")
data object HistoryRootKey : NavKey
