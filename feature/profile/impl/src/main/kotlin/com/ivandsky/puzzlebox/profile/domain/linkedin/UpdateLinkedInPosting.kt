package com.ivandsky.puzzlebox.profile.domain.linkedin

import com.ivandsky.puzzlebox.data.linkedin.LinkedInLink

/**
 * Turns posting results to LinkedIn on or off. Turning on stamps the time the player accepted the posting warning,
 * so the caller must have shown it first; this use case does not ask. Turning off needs no consent and no
 * confirmation. It does nothing while LinkedIn is not connected, and it never reads or changes the link state.
 */
class UpdateLinkedInPosting(
    private val link: LinkedInLink,
    private val clock: () -> Long,
) {

    suspend operator fun invoke(on: Boolean) {
        link.setPosting(if (on) clock() else null)
    }
}
