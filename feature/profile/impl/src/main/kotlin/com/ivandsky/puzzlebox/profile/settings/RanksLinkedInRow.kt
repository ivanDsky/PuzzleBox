package com.ivandsky.puzzlebox.profile.settings

/**
 * Whether Settings' Ranks block carries the "LinkedIn games only" switch, as the app's DI read of its flag (backlog
 * #1770), so the profile feature reads no history flag. Absent from the graph, or [shown] false, Settings is as before.
 */
class RanksLinkedInRow(val shown: Boolean)
