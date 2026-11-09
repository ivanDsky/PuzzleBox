package com.ivandsky.puzzlebox.domain

/** The Duels list in display order, and [needYou] = rows of kind YourMove, RevealReady or Invited (the badge). */
data class MyDuels(val rows: List<DuelRow>, val needYou: Int)
