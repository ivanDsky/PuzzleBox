package com.ivandsky.puzzlebox.tools.puzzlecli

/** An in-memory disk behind an [IndexIo]. [corrupt] rewrites what [IndexIo.readText] returns for a path, to fake a bad write. */
class MemoryFiles(private val corrupt: (path: String, text: String) -> String = { _, text -> text }) {
    val files: MutableMap<String, String> = HashMap()

    val io: IndexIo = IndexIo(
        readText = { path -> files[path]?.let { corrupt(path, it) } },
        appendLine = { path, line -> files[path] = files[path].orEmpty() + line + "\n" },
        writeText = { path, text -> files[path] = text },
    )
}
