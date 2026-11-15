package com.ivandsky.puzzlebox.benchmark

/**
 * The only package the journeys may drive: :app's `benchmark` build type, signed out, with its
 * own data. The release package (com.ivandsky.puzzlebox) holds the real account and is never a target.
 */
internal const val TARGET_PACKAGE = "com.ivandsky.puzzlebox.benchmark"
