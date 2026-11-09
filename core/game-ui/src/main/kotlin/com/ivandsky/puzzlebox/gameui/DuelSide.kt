package com.ivandsky.puzzlebox.gameui

/** One player in [DuelCompare]: who they are and what they have to show. */
data class DuelSide(val name: String, val photoUrl: String?, val state: DuelSideState)
