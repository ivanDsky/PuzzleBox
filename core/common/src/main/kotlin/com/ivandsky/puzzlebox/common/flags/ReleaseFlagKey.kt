package com.ivandsky.puzzlebox.common.flags

/**
 * One release flag (ADR-0022): what an owner module's `<Owner>Flag` enum implements.
 *
 * @property key the flag's SharedPreferences key in `release_flags`, where a debug override is stored: an enum's
 *   entry `name`. Never change it, so an override survives the flag's move between modules.
 * @property label what the flag turns on, shown in the debug Settings' Release flags section.
 * @property releaseDefault the value every release build uses; debug builds start from it too.
 */
interface ReleaseFlagKey {
    val key: String
    val label: String
    val releaseDefault: Boolean
}
