package com.ivandsky.puzzlebox.data.linkedin

/**
 * What the Post to LinkedIn button of one game day shows: its [state], and the [timeMs] of the solve a post sends and
 * LinkedIn holds, the day's record row, never another solve of the day. Null while the day has no record row.
 */
data class LinkedInPostView(val state: LinkedInPostState, val timeMs: Long?)
