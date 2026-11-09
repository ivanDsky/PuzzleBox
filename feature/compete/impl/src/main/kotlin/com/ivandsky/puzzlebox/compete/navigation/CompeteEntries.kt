package com.ivandsky.puzzlebox.compete.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.compete.api.DuelKey
import com.ivandsky.puzzlebox.compete.duel.DuelRoute
import com.ivandsky.puzzlebox.navigation.Navigator

/** Registers compete's entries; 36, 37 and 38 add theirs (they need [navigator]). The duel screen is [DuelKey]'s. */
fun EntryProviderScope<NavKey>.competeEntry(navigator: Navigator) {
    entry<DuelKey> { key -> DuelRoute(key.code, navigator) }
}
