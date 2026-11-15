package com.ivandsky.puzzlebox.game.linkedin

sealed interface LinkedInPlayValue {
    data class Strings(val values: List<String>) : LinkedInPlayValue
    data class Number(val value: Int) : LinkedInPlayValue
}
