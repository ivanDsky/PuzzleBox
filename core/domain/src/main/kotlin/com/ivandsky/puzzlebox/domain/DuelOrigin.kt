package com.ivandsky.puzzlebox.domain

/** Where a duel solve came from: the match and, when still known locally, the opponent's name. */
data class DuelOrigin(val matchCode: String, val opponentName: String?)
