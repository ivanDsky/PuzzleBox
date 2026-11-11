package com.ivandsky.puzzlebox.data.linkedin

/**
 * The player's own row on a day's LinkedIn board: their LinkedIn [name] and the absolute path of their cached
 * photo ([photoPath], null while none has been downloaded). It is the player, never one of the connections.
 */
data class LinkedInOwner(val name: String, val photoPath: String? = null)
