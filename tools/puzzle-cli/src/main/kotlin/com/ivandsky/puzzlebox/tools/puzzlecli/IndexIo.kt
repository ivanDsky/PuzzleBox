package com.ivandsky.puzzlebox.tools.puzzlecli

import java.io.File

/**
 * The index pass's file seams, so tests stay in memory like `run`'s `read` lambda.
 *
 * @property readText the file's text, or null when it does not exist
 * @property appendLine appends one line (and its newline) to a file, created if missing, and flushes it
 * @property writeText replaces a file's whole text, creating parent directories
 */
class IndexIo(
    val readText: (path: String) -> String?,
    val appendLine: (path: String, line: String) -> Unit,
    val writeText: (path: String, text: String) -> Unit,
) {
    companion object {
        /** The real disk. Each append opens, writes and closes, so a settled line is on disk before the next fetch. */
        fun files(): IndexIo = IndexIo(
            readText = { path -> File(path).takeIf { it.exists() }?.readText() },
            appendLine = { path, line -> File(path).appendText(line + "\n") },
            writeText = { path, text ->
                val file = File(path)
                file.absoluteFile.parentFile?.mkdirs()
                file.writeText(text)
            },
        )
    }
}
