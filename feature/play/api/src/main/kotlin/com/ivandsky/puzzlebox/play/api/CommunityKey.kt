package com.ivandsky.puzzlebox.play.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Community tab root (top level; nav-rethink M10-1): the shelves home with New today, your friends' puzzles and your own
 * puzzles, and Make a puzzle.
 */
@Serializable
@SerialName("com.ivandsky.puzzlebox.play.api.CommunityKey")
data object CommunityKey : NavKey
