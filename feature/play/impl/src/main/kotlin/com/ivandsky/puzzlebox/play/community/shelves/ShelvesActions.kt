package com.ivandsky.puzzlebox.play.community.shelves

import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode

/**
 * What a tap on the shelves home does. Search, the two entry tiles and Make belong to the tickets that build their
 * pages (06, 07), so they default to nothing until the route wires them.
 *
 * [onMake] opens the New puzzle sheet; a [GameId] is the game whose Make card was tapped, null from the empty hero.
 */
internal class ShelvesActions(
    val onEnterCode: () -> Unit,
    val onOpen: (ShareCode) -> Unit,
    val onRetry: () -> Unit,
    val onSearch: () -> Unit = {},
    val onFriends: () -> Unit = {},
    val onYours: () -> Unit = {},
    val onMake: (GameId?) -> Unit = {},
)
