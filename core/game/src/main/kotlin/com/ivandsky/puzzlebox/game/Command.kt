package com.ivandsky.puzzlebox.game

/**
 * One reversible player action over a game's board state [S] — the Command pattern, one
 * implementation per KIND of move (Zip's Extend/RetractTo/Reset, Tango's Place, Queens'
 * PlaceQueen/RemoveQueen/ToggleCross/Clear). A command stores ONLY the delta its own
 * inverse needs (the retracted tail, the replaced value), never a copy of the board, so
 * history cost is per action, not per action × board.
 *
 * Contract: [revert] is the exact inverse of [apply] on the state [apply] produced —
 * `revert(apply(s)) == s` for every `s` the command was built against. Commands are
 * immutable values: they serialize with their game's session codec and compare by value.
 *
 * [S] is the BOARD state the player edits (a path, a placement map, a queen set + manual
 * marks) — not the session: counters, timer and the history itself stay outside, so
 * undoing a move never rewinds the clock, a hint count, or a mistake tally.
 */
interface Command<S> {
    fun apply(state: S): S
    fun revert(state: S): S
}

/**
 * Immutable undo/redo history over [Command]s of type [C]. [done] is oldest-first (the
 * last element is what [undo] reverts); [undone] is the redo stack, last element = what
 * [redo] re-applies. [record] drops the redo stack — a new action forks the timeline.
 */
class CommandHistory<S, C : Command<S>>(
    val done: List<C> = emptyList(),
    val undone: List<C> = emptyList(),
) {
    val canUndo: Boolean get() = done.isNotEmpty()
    val canRedo: Boolean get() = undone.isNotEmpty()

    /** [command] has just been applied by the caller: push it, forget the redo stack. */
    fun record(command: C): CommandHistory<S, C> = CommandHistory(done + command, emptyList())

    /** Swap the newest command for [command] (a gesture growing its own unit); [command] is just applied. */
    fun replaceLast(command: C): CommandHistory<S, C> = CommandHistory(done.dropLast(1) + command, emptyList())

    /** Revert the newest command on [state]; null when there is nothing to undo. */
    fun undo(state: S): Step<S, C>? {
        val command = done.lastOrNull() ?: return null
        return Step(command.revert(state), CommandHistory(done.dropLast(1), undone + command))
    }

    /** Re-apply the most recently undone command on [state]; null when nothing to redo. */
    fun redo(state: S): Step<S, C>? {
        val command = undone.lastOrNull() ?: return null
        return Step(command.apply(state), CommandHistory(done + command, undone.dropLast(1)))
    }

    /** The board state plus the history that goes with it after an undo/redo. */
    data class Step<S, C : Command<S>>(val state: S, val history: CommandHistory<S, C>)

    override fun equals(other: Any?): Boolean =
        other is CommandHistory<*, *> && done == other.done && undone == other.undone

    override fun hashCode(): Int = 31 * done.hashCode() + undone.hashCode()

    override fun toString(): String = "CommandHistory(done=$done, undone=$undone)"
}
