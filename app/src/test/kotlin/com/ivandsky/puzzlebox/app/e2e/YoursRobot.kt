package com.ivandsky.puzzlebox.app.e2e

import com.ivandsky.puzzlebox.play.api.CommunityKey

/** The Community tab's Yours page: where the drafts and the published puzzles of the person holding the phone are listed and opened. */
internal fun ShellRobot.openYours() {
    tapTab(CommunityKey)
    tap("shelves.entry.yours")
}
