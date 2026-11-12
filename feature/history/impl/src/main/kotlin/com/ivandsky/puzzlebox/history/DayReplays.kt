package com.ivandsky.puzzlebox.history

/** The replays of the day [key] names. A screen shows them only for that day, so a switch never flashes the last day's. */
class DayReplays(val key: String?, val replays: HistoryReplays)
