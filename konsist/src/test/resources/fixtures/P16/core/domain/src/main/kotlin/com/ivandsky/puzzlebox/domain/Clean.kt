package com.ivandsky.puzzlebox.domain

/** Takes the flag's value, not the flag. */
class Clean(private val polish: Boolean) {
    operator fun invoke() = polish
}
