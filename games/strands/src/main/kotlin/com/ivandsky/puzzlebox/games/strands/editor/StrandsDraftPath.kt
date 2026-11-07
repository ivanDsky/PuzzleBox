package com.ivandsky.puzzlebox.games.strands.editor

/** One traced path: its [cells] as row-major indices in trace order, and its [word], null while unnamed. */
data class StrandsDraftPath(val cells: List<Int>, val word: String?)
