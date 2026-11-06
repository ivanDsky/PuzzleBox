package com.ivandsky.puzzlebox.network

/** Latest release of the app's repo; null on any failure. Never throws bar cancellation. */
fun interface GitHubReleaseTransport {
    suspend fun latest(): GitHubRelease?
}
