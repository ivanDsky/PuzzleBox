package com.ivandsky.puzzlebox.home

class AnnotatedAliasViewModel : ViewModel() {
    private val _state = MutableStateFlow(0)

    @get:JvmName("current")
    val state = _state
}
