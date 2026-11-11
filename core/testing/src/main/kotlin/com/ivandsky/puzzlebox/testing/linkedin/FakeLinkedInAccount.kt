package com.ivandsky.puzzlebox.testing.linkedin

import com.ivandsky.puzzlebox.data.linkedin.LinkedInAccount
import com.ivandsky.puzzlebox.data.linkedin.LinkedInAccountData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Scripted [LinkedInAccount]. It reads no database and derives nothing: a test sets the exact
 * [LinkedInAccountData] the phone reports through [emit]; until then it reports an empty phone.
 */
class FakeLinkedInAccount : LinkedInAccount {

    private val current = MutableStateFlow(EMPTY)

    override fun observe(): Flow<LinkedInAccountData> = current

    fun emit(data: LinkedInAccountData) {
        current.value = data
    }

    private companion object {
        val EMPTY = LinkedInAccountData(owner = null, connectionsSeen = 0, lastFetchedAtMs = null)
    }
}
