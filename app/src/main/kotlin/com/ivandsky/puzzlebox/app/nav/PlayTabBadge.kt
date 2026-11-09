package com.ivandsky.puzzlebox.app.nav

import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.play.api.PlayKey

/**
 * The tabs that carry a dot: Play while any of today's games is in progress or duels need
 * the player. With the Competitions flag off the app passes `duelsNeedYou = 0`.
 */
internal fun playTabBadged(anyInProgress: Boolean, duelsNeedYou: Int): Set<NavKey> =
    if (anyInProgress || duelsNeedYou > 0) setOf(PlayKey) else emptySet()
