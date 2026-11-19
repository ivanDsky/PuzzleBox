package com.ivandsky.puzzlebox.common.flags

/**
 * The release flags one module declares (ADR-0022), contributed to Koin with `flagSet` and gathered by `:app` into
 * [DeclaredFlags]. [owner] names the module's set: it orders the sets and names both sides of a repeated key.
 */
class FlagSet(val owner: String, val flags: List<ReleaseFlagKey>)
