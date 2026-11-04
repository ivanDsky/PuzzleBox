package com.ivandsky.puzzlebox.home

class AnnotatedGetterViewModel : ViewModel() {
    private val _state = MutableStateFlow(0)
    val state @Deprecated(message = "a = b") get() = _state
}
