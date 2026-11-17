package com.ivandsky.puzzlebox.play.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Community tab root (top level), a tab only while CustomInGames and CommunityPuzzles are both on (nav-rethink M10-1):
 * New today, your puzzles and your friends' puzzles, and Make a puzzle. Its key stays registered with the tab off so a
 * saved state with it still decodes; restore drops a stack whose root is no longer a tab.
 */
@Serializable
@SerialName("com.ivandsky.puzzlebox.play.api.CommunityKey")
data object CommunityKey : NavKey
