package com.ivandsky.puzzlebox.profile.whatsnew

/** Where the What's new page gets its releases, newest first. A bad source throws; the caller shows the load-failed state. */
interface WhatsNewSource {
    fun releases(): List<WhatsNewRelease>
}
