package com.ivandsky.puzzlebox.data.leaderboard

import java.time.LocalDate

/**
 * A22: a Final board can never change again, so it is read once and kept. The rules accept an
 * entry only for the server's UTC date −2…+1 (ADR-0024 decision 2); three days leaves one day
 * of device-clock skew before a device could call a still-writable board Final.
 */
fun LeaderboardRef.isFinal(utcToday: LocalDate): Boolean = day < utcToday.minusDays(FINAL_AFTER_DAYS)

private const val FINAL_AFTER_DAYS = 3L
