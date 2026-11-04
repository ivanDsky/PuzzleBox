package com.ivandsky.puzzlebox.konsist.core

import com.lemonappdev.konsist.api.declaration.KoFileDeclaration

/** path = root-relative, '/'-separated; sourceSet = dir name ("main", "debug", "testDebug").
 *  packageName = the Konsist-parsed header, "" when there is none; ko = the file as Konsist parsed it.
 *  strippedText = comments blanked ([CommentStripper.strip]); codeText = comments and literal contents blanked
 *  ([CommentStripper.code]). Both keep every offset and newline of text. */
data class SourceFile(
    val path: String,
    val module: Module,
    val sourceSet: String,
    val kind: SourceSetKind,
    val packageName: String,
    val ko: KoFileDeclaration,
    val text: String,
    val strippedText: String,
    val codeText: String,
)
