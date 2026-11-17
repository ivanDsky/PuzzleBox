package com.ivandsky.puzzlebox.create.editor

import com.ivandsky.puzzlebox.data.model.SharedPuzzleDoc.Visibility

/**
 * Who a publish is for (nav-rethink C1), held by one open [PublishSession] and never stored: a draft does not
 * remember it, so a reopened Studio starts at [Visibility.Public] again.
 *
 * Once an attempt falls short the pick is [Choice.locked]: the retry goes out as the same publish, because a lost answer
 * may already have landed the first and a changed choice would be a different doc under a new code.
 */
class PublishVisibility {
    private var pick = Visibility.Public
    private var locked = false

    /** What the picker shows. */
    val choice: Choice get() = Choice(pick, locked)

    /** What goes out with the next attempt. */
    val sent: Visibility get() = pick

    /** Takes [next] while the pick is not locked; whether the choice changed. */
    fun pick(next: Visibility): Boolean {
        if (locked || next == pick) return false
        pick = next
        return true
    }

    /** The last attempt ended: [retry] (it fell short, another try may fix it) freezes the pick; a fresh pick never unfreezes. */
    fun attempted(retry: Boolean): Boolean {
        if (!retry || locked) return false
        locked = true
        return true
    }

    /** The pick the picker shows; [locked] turns its chips off. */
    data class Choice(val pick: Visibility, val locked: Boolean = false)
}
