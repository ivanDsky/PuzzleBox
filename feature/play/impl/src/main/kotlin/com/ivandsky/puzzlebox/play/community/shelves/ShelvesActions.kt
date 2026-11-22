package com.ivandsky.puzzlebox.play.community.shelves

import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.community.shelves.seeall.SeeAllScope

/**
 * What a tap on the shelves home does. Search, the two entry tiles and a shelf's See all open their pages through the
 * route; they default to nothing so a screen shown alone does not need them. Search and From friends are the
 * See all page's [SeeAllScope.Search] and [SeeAllScope.Friends]; [onSeeAll] is a shelf's own scope.
 *
 * [onMake] starts a puzzle; a [GameId] is the game whose Make card was tapped, null from the empty hero (the New puzzle
 * sheet). [onReport] and [onRemoveFriend] are a card's long-press menu.
 */
internal class ShelvesActions(
    val onEnterCode: () -> Unit,
    val onOpen: (ShareCode) -> Unit,
    val onRetry: () -> Unit,
    val onSearch: () -> Unit = {},
    val onFriends: () -> Unit = {},
    val onYours: () -> Unit = {},
    val onSeeAll: (SeeAllScope) -> Unit = {},
    val onMake: (GameId?) -> Unit = {},
    val onReport: (ShareCode) -> Unit = {},
    val onRemoveFriend: (ShareCode) -> Unit = {},
)
