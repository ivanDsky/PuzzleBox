package com.ivandsky.puzzlebox.resultui

import java.time.format.DateTimeFormatter
import java.util.Locale

/** The main button's words and test tag. */
internal data class ResultPrimaryStep(val text: String, val tag: String)

/** The main button of this action, or null for [ResultPrimaryAction.AllDone], whose place the countdown takes. */
internal fun ResultPrimaryAction.step(locale: Locale = Locale.getDefault()): ResultPrimaryStep? = when (this) {
    is ResultPrimaryAction.Next -> ResultPrimaryStep("Next: $name", "result.next")
    is ResultPrimaryAction.NextDay -> ResultPrimaryStep("Next: $name · ${day.format(DateTimeFormatter.ofPattern("MMM d", locale))}", "result.next")
    ResultPrimaryAction.AllDone -> null
    ResultPrimaryAction.Done -> ResultPrimaryStep("Done", "result.done")
    is ResultPrimaryAction.ChallengeFriend -> ResultPrimaryStep("Challenge $name", "result.challengeMain")
    is ResultPrimaryAction.MissedDay -> ResultPrimaryStep("Play ${heroWeekdayName(day.dayOfWeek, locale)}'s $gameName", "result.missedDay")
    is ResultPrimaryAction.FriendPuzzle -> ResultPrimaryStep("Play $authorName's puzzle", "result.friendPuzzle")
    is ResultPrimaryAction.CommunityPuzzle -> ResultPrimaryStep("Try a new community puzzle by $authorName", "result.communityPuzzle")
}

/** The Challenge row's name: [challenge], unless the main button already is that challenge. */
internal fun visibleChallenge(
    primary: ResultPrimaryAction?,
    challenge: ResultPrimaryAction.ChallengeFriend?,
): ResultPrimaryAction.ChallengeFriend? = challenge.takeUnless { primary is ResultPrimaryAction.ChallengeFriend }
