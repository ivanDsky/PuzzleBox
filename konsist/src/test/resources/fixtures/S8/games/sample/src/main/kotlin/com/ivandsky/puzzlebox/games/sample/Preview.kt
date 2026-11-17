package com.ivandsky.puzzlebox.games.sample

annotation class Preview(val name: String = "", val group: String = "")

@Preview(name = "Crown mode", group = "Leaderboard")
fun preview() = Unit

fun testTag(tag: String) = tag

val tagged = testTag("Leaderboard.Crown")
