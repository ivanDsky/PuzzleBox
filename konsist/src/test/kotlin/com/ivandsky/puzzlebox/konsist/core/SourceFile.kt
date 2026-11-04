package com.ivandsky.puzzlebox.konsist.core

/** path = root-relative, '/'-separated; sourceSet = dir name ("main", "debug", "testDebug"). */
data class SourceFile(
    val path: String,
    val module: Module,
    val sourceSet: String,
    val kind: SourceSetKind,
    val packageName: String,
    val text: String,
    val strippedText: String,
)
