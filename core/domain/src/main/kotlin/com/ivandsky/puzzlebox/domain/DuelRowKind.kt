package com.ivandsky.puzzlebox.domain

/** What a duel row asks of the player; the first three need them (see [MyDuels.needYou]). */
enum class DuelRowKind { Invited, YourMove, RevealReady, Waiting, Unclaimed }
