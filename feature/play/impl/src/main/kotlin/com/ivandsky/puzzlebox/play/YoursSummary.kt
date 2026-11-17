package com.ivandsky.puzzlebox.play

/**
 * What Community's Yours tab needs to know about your own puzzles without drawing them: how many there are (every
 * draft and every published puzzle) for the tab's count, and the share code of the first published puzzle, the
 * newest, for the tablet's default pick; null when none is published.
 */
data class YoursSummary(val count: Int, val firstPublishedCode: String?) {
    companion object {
        /** Nothing made yet; also what a [CustomSlot] without a Yours tab reports. */
        val None = YoursSummary(count = 0, firstPublishedCode = null)
    }
}
