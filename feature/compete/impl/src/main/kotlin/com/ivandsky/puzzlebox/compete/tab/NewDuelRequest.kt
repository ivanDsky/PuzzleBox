package com.ivandsky.puzzlebox.compete.tab

import com.ivandsky.puzzlebox.compete.create.DuelChallenge

/** A New duel being composed in the tablet's pane; [challenge] fills it in. */
internal data class NewDuelRequest(val challenge: DuelChallenge? = null)
