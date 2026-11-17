package com.ivandsky.puzzlebox.games.sample

fun label(n: Int) = "${if (n == 1) "1 crown" else "$n crowns"}"

fun describe() = describe(
    text = "Round-up of colours",
)

fun describe(text: String) = text
