package com.ivandsky.puzzlebox.domain.linkedin

/** What the day card shows of one game day's LinkedIn data: a status line and the connections board. */
data class LinkedInDayView(val status: LinkedInDayStatus, val board: LinkedInBoardView)
