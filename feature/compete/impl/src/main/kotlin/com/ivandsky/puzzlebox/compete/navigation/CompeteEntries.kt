package com.ivandsky.puzzlebox.compete.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.compete.api.DuelKey
import com.ivandsky.puzzlebox.compete.api.DuelRevealKey
import com.ivandsky.puzzlebox.compete.duel.DuelRoute
import com.ivandsky.puzzlebox.compete.reveal.RevealRoute
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.navigation.Navigator

/**
 * Registers compete's entries; 38 adds the summary's (it needs [navigator]). The duel screen is [DuelKey]'s and the
 * reveal [DuelRevealKey]'s; [layout] is the app's play layout, which puts the duel's puzzles in two panes on an
 * expanded landscape window and sizes the reveal's intro for it.
 */
fun EntryProviderScope<NavKey>.competeEntry(navigator: Navigator, layout: PlayLayout) {
    entry<DuelKey> { key -> DuelRoute(key.code, navigator, layout) }
    entry<DuelRevealKey> { key -> RevealRoute(key.code, navigator, layout) }
}
