package com.ivandsky.puzzlebox.domain

class SaveTwice {
    operator fun invoke(): Int = 1
    operator fun invoke(x: Int): Int = x
}
