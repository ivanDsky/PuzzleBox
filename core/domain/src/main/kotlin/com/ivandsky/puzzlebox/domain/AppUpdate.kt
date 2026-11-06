package com.ivandsky.puzzlebox.domain

/** A newer release the player can open. */
data class AppUpdate(val version: AppVersion, val url: String)
