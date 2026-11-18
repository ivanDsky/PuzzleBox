package com.ivandsky.puzzlebox.profile.whatsnew

/** One release's notes. [date] stays the file's ISO `yyyy-mm-dd` string; showing it is the screen's job. */
data class WhatsNewRelease(val version: String, val date: String, val notes: List<String>)
