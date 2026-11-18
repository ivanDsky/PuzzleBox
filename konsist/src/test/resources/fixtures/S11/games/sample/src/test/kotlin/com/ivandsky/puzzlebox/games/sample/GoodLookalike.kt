package com.ivandsky.puzzlebox.games.sample

annotation class IgnoredOnParcel

class GoodLookalike {
    @IgnoredOnParcel
    val cached = 1

    fun label() = listOf(1).forEach Ignore@{ if (it > 0) return@Ignore }
}
