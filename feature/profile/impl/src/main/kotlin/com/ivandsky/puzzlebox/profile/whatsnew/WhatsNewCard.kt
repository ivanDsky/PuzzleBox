package com.ivandsky.puzzlebox.profile.whatsnew

/** One release as the page shows it: [date] is already worded (`18 Nov 2026`). */
internal data class WhatsNewCard(val version: String, val date: String, val notes: List<String>)
