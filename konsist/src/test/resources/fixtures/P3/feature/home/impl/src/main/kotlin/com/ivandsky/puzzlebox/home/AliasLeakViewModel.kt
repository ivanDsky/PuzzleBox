package com.ivandsky.puzzlebox.home

class AliasLeakViewModel : ViewModel() {
    private val _state = MutableStateFlow(0)
    val state = _state
}
