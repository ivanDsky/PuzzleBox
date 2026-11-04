package com.ivandsky.puzzlebox.domain

class CreateHidden {
    operator fun invoke(): Int = 1
    private operator fun invoke(x: Int): Int = x
    fun invoke(x: String): Int = 1
}
