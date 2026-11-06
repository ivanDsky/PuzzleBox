package com.ivandsky.puzzlebox.data.update

/** Source of the latest published app release. */
interface AppReleaseRepository {
    /** Latest published release, or null when unknown (offline, rate-limited, private repo). */
    suspend fun latest(): AppRelease?
}
