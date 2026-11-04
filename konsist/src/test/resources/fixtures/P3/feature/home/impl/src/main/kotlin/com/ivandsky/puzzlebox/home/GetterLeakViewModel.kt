package com.ivandsky.puzzlebox.home

class GetterLeakViewModel : ViewModel() {
    private val _state = MutableStateFlow(0)
    val state get() = _state
}
