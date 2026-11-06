package com.ivandsky.puzzlebox.testing.repository

import com.ivandsky.puzzlebox.data.update.AppRelease
import com.ivandsky.puzzlebox.data.update.AppReleaseRepository

/** Settable [AppReleaseRepository]: returns [release] verbatim and counts [calls]. */
class FakeAppReleaseRepository(var release: AppRelease? = null) : AppReleaseRepository {

    var calls = 0
        private set

    override suspend fun latest(): AppRelease? {
        calls++
        return release
    }
}
