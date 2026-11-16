package com.ivandsky.puzzlebox.create.editor

import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.common.flags.isEnabled
import com.ivandsky.puzzlebox.data.model.SharedPuzzleDoc.Visibility

/**
 * Who a publish is for (nav-rethink C1), held by one open [PublishSession] and never stored: a draft does not
 * remember it, so a reopened Studio starts at [Public] again.
 *
 * It is offered only while [ReleaseFlag.CommunityPuzzles] is on, read once when the session opens (spec A2). Off, nothing
 * is offered and [sent] is always [Visibility.LinkOnly], which the doc writer leaves out: the doc is the one written today.
 * Once an attempt falls short the pick is [Choice.locked]: the retry goes out as the same publish, because a lost answer
 * may already have landed the first and a changed choice would be a different doc under a new code.
 */
class PublishVisibility(flags: ReleaseFlags) {
    private val offered = flags.isEnabled(ReleaseFlag.CommunityPuzzles)
    private var pick = Visibility.Public
    private var locked = false

    /** What the picker shows; null while nothing is offered (flag off). */
    val choice: Choice? get() = if (offered) Choice(pick, locked) else null

    /** What goes out with the next attempt. */
    val sent: Visibility get() = if (offered) pick else Visibility.LinkOnly

    /** Takes [next] while the pick is offered and not locked; whether the choice changed. */
    fun pick(next: Visibility): Boolean {
        if (!offered || locked || next == pick) return false
        pick = next
        return true
    }

    /** The last attempt ended: [retry] (it fell short, another try may fix it) freezes the pick; a fresh pick never unfreezes. */
    fun attempted(retry: Boolean): Boolean {
        if (!offered || !retry || locked) return false
        locked = true
        return true
    }

    /** The pick the picker shows; [locked] turns its chips off. */
    data class Choice(val pick: Visibility, val locked: Boolean = false)
}
