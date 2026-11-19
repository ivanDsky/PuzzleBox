package com.ivandsky.puzzlebox.insights.recap

import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.insights.domain.recap.RecapPeriod

/** What a recap shows. The cards are computed once when the recap opens, so the pages never shift under the player. */
internal sealed interface RecapUiState {

    /** The cards are being computed. */
    data object Loading : RecapUiState

    /** The period has no card yet, and this account's first cloud restore is still pulling: the history is coming back (A23). */
    data object Restoring : RecapUiState

    /** The period has nothing to recap. */
    data object Empty : RecapUiState

    /** The story: [cards] in order, opening on the page at [initialPage] (a saved position, kept inside the cards). */
    data class Ready(val period: RecapPeriod, val cards: List<RecapCard>, val initialPage: Int) : RecapUiState
}
