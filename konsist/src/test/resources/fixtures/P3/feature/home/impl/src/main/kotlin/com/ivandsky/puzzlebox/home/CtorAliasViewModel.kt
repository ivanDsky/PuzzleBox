package com.ivandsky.puzzlebox.home

class CtorAliasViewModel(private val source: MutableStateFlow<Int>) : ViewModel() {
    val state = source
}
