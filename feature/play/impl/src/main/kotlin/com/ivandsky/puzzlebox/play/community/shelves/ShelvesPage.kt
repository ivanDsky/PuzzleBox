package com.ivandsky.puzzlebox.play.community.shelves

import androidx.compose.runtime.saveable.Saver
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.play.community.shelves.seeall.SeeAllScope

/**
 * A page that opens over the shelves home: a See all page of one [SeeAllScope], or Your puzzles. The home is the stack's
 * bottom and a page is its only other entry (no page opens another), so the route holds a `ShelvesPage?`.
 */
internal sealed interface ShelvesPage {
    data class SeeAll(val scope: SeeAllScope) : ShelvesPage

    data object Yours : ShelvesPage
}

private const val GAME_PREFIX = "game:"

/** Saves the open page as text so a rotation and process death keep it; text this cannot read restores the home. */
internal val ShelvesPageSaver: Saver<ShelvesPage?, String> = Saver(
    save = { page ->
        when (page) {
            null -> ""
            ShelvesPage.Yours -> "yours"
            is ShelvesPage.SeeAll -> when (val scope = page.scope) {
                SeeAllScope.MostPlayed -> "most"
                SeeAllScope.Newest -> "newest"
                SeeAllScope.Friends -> "friends"
                SeeAllScope.Search -> "search"
                is SeeAllScope.Game -> GAME_PREFIX + scope.game.value
            }
        }
    },
    restore = { text ->
        when {
            text == "yours" -> ShelvesPage.Yours
            text == "most" -> ShelvesPage.SeeAll(SeeAllScope.MostPlayed)
            text == "newest" -> ShelvesPage.SeeAll(SeeAllScope.Newest)
            text == "friends" -> ShelvesPage.SeeAll(SeeAllScope.Friends)
            text == "search" -> ShelvesPage.SeeAll(SeeAllScope.Search)
            text.startsWith(GAME_PREFIX) -> ShelvesPage.SeeAll(SeeAllScope.Game(GameId(text.removePrefix(GAME_PREFIX))))
            else -> null
        }
    },
)
