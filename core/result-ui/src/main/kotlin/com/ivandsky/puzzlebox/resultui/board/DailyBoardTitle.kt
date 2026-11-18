package com.ivandsky.puzzlebox.resultui.board

/** The "<Game> · today" board title (A15); "Today" when the game name is unknown. */
fun dailyBoardTitle(gameName: String?): String = if (gameName != null) "$gameName · today" else "Today"
