package com.ivandsky.puzzlebox.play.community.shelves

import androidx.compose.runtime.Composable
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.CustomSlot
import com.ivandsky.puzzlebox.play.community.shelves.seeall.SeeAllPage
import com.ivandsky.puzzlebox.play.community.shelves.yours.YourPuzzlesPage

/** The screen of [page]: a See all page of its scope, or Your puzzles over [custom]'s list. A row opens through [onOpen]. */
@Composable
internal fun ShelvesPageContent(page: ShelvesPage, custom: CustomSlot, onBack: () -> Unit, onOpen: (ShareCode) -> Unit) {
    when (page) {
        is ShelvesPage.SeeAll -> SeeAllPage(page.scope, onBack, onOpen)
        ShelvesPage.Yours -> YourPuzzlesPage(custom.yours, onBack)
    }
}
