package com.ivandsky.puzzlebox.data.repository

/**
 * Whether a first entry on a shared puzzle's board also bumps the puzzle's `solves` (#2032). The play feature binds
 * it to its own release flag (ADR-0022: the owner reads its flag and hands a Boolean down); the backend asks it at
 * each [CustomBoardRepository.submit], so it is read per write, never cached.
 */
fun interface SolveCounterSwitch {
    fun isOn(): Boolean
}
