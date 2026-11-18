package com.ivandsky.puzzlebox.b

import com.ivandsky.puzzlebox.domain.GetShared
import com.ivandsky.puzzlebox.domain.ObserveNested.Binding
import com.ivandsky.puzzlebox.domain.stars.*

// com.ivandsky.puzzlebox.domain.GetCommented is named only in a comment and a string here.
class BScreen(val shared: GetShared, val starred: GetStarred) {
    val label = "com.ivandsky.puzzlebox.domain.GetCommented"

    fun bind(binding: Binding) = binding
}
