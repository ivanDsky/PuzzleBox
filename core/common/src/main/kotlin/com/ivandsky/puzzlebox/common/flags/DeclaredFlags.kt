package com.ivandsky.puzzlebox.common.flags

/**
 * Every release flag the app declares (ADR-0022): the flag sets ordered by owner, each set in its declaration order,
 * since Koin's `getAll` order is not stable. The shipped and debug flags and the debug Release flags section read
 * [all]. A repeated key throws, so two sets can never share a stored override. A repeated owner is caught by konsist
 * P15, not here: Koin keeps the last set registered under one owner and drops the other.
 */
class DeclaredFlags(sets: List<FlagSet>) {
    val all: List<ReleaseFlagKey>

    init {
        val sorted = sets.sortedBy { it.owner }
        val ownerByKey = mutableMapOf<String, String>()
        sorted.forEach { set ->
            set.flags.forEach { flag ->
                ownerByKey.put(flag.key, set.owner)?.let { first ->
                    error("release flag key '${flag.key}' is declared by both '$first' and '${set.owner}'")
                }
            }
        }
        all = sorted.flatMap { it.flags }
    }
}
