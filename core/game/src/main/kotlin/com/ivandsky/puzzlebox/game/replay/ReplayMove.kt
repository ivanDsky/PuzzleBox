package com.ivandsky.puzzlebox.game.replay

/** One recorded player action. The game owns the meaning of [kind] and [args]. */
class ReplayMove(val kind: Int, val args: IntArray) {

    override fun equals(other: Any?): Boolean =
        other is ReplayMove && kind == other.kind && args.contentEquals(other.args)

    override fun hashCode(): Int = 31 * kind + args.contentHashCode()

    override fun toString(): String = "ReplayMove(kind=$kind, args=${args.contentToString()})"
}
