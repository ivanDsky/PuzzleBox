package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue

/** Which way the last move went; the deck throws a card away going [Forward] and brings it back going [Back]. */
internal enum class DeckMove { Forward, Back }

/**
 * Where the recap story is: a page (every card, then the Share page when there is one), an optional cover before the
 * first card, the way the last move went and whether the player is holding. Nothing here draws or animates; the deck,
 * the cover and the strip read it, and tests drive it without a screen.
 *
 * [initialPage] is the saved card index and is clamped to the last page. With [coverFirst] a story opened on page 0
 * starts on the cover; a saved page past 0 resumes on that card with the cover already dismissed.
 */
internal class RecapDeckState(pageCount: Int, initialPage: Int = 0, coverFirst: Boolean = false) {
    var pageCount by mutableIntStateOf(pageCount)
        private set
    var page by mutableIntStateOf(initialPage.coerceIn(0, lastPage(pageCount)))
        private set
    var onCover by mutableStateOf(coverFirst && page == 0)
        private set
    var move by mutableStateOf(DeckMove.Forward)
        private set
    var holding by mutableStateOf(false)
        private set

    /** Bumps each time the player asks the card on top to play its entrance again; the deck restarts its face on it. */
    var entrance by mutableIntStateOf(0)
        private set

    /** True from a fresh open until the deal lands; a restored story is already dealt. */
    var dealing by mutableStateOf(true)
        private set

    /** Segments the strip draws: one per page, the cover none. */
    val segmentCount: Int get() = pageCount

    /** Segments filled from the left: through the current page, none on the cover. */
    val filledSegments: Int get() = if (onCover) 0 else page + 1

    /** The card the deck has on top: the cover first, then each page after it. */
    val position: Int get() = if (onCover) 0 else page + 1

    /** Cards in the deck: the cover and every page. */
    val stackSize: Int get() = pageCount + 1

    /** The tablet fact the list outlines: the current page, none on the cover. */
    val selectedFact: Int get() = if (onCover) NoFact else page

    /** Whether a step back stays inside the story; false on the cover and the first card, where back leaves the recap. */
    val canStepBack: Boolean get() = !onCover && page > 0

    /** Steps on: off the cover onto the first card, then card to card, stopping on the last page. Returns whether it moved. */
    fun forward(): Boolean = when {
        onCover -> { onCover = false; move = DeckMove.Forward; true }
        page < lastPage(pageCount) -> { page++; move = DeckMove.Forward; true }
        else -> false
    }

    /** Steps back one card, stopping at the first. Returns whether it moved. */
    fun back(): Boolean {
        if (!canStepBack) return false
        page--
        move = DeckMove.Back
        return true
    }

    /** Goes to [target], kept inside the pages, dismissing the cover. */
    fun jumpTo(target: Int) {
        val clamped = target.coerceIn(0, lastPage(pageCount))
        move = if (clamped >= page) DeckMove.Forward else DeckMove.Back
        page = clamped
        onCover = false
    }

    /** Back to the cover, and with it no fact selected. */
    fun replay() {
        page = 0
        onCover = true
        move = DeckMove.Back
    }

    fun hold(holding: Boolean) {
        this.holding = holding
    }

    fun restartEntrance() {
        entrance++
    }

    fun dealt() {
        dealing = false
    }

    /** Follows the story gaining a page (the friends card arrives late) without moving the one on screen; a loss clamps. */
    fun resize(pageCount: Int) {
        if (pageCount == this.pageCount) return
        this.pageCount = pageCount
        page = page.coerceAtMost(lastPage(pageCount))
    }

    companion object {
        const val NoFact = -1

        /** Keeps the page and the cover across a configuration change or process death; a hold ends with the touch. */
        val Saver = listSaver<RecapDeckState, Any>(
            save = { listOf(it.pageCount, it.page, it.onCover) },
            restore = { saved ->
                RecapDeckState(saved[0] as Int, saved[1] as Int).also {
                    it.onCover = saved[2] as Boolean
                    it.dealing = false
                }
            },
        )
        private fun lastPage(pageCount: Int) = (pageCount - 1).coerceAtLeast(0)
    }
}
