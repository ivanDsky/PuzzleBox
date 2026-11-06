package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.update.AppReleaseRepository

/** Compares the latest published release against the installed [AppBuild] (spec A3, A4, A6). */
class GetAppUpdate(private val releases: AppReleaseRepository, private val build: AppBuild) {

    /** Null unless checks are on, both versions parse, and latest > installed. Never throws bar cancellation. */
    suspend operator fun invoke(): AppUpdate? {
        if (!build.checksForUpdates) return null
        val installed = AppVersion.parse(build.versionName) ?: return null
        val release = releases.latest() ?: return null
        val latest = AppVersion.parse(release.tag) ?: return null
        return if (latest > installed) AppUpdate(latest, release.url) else null
    }
}
