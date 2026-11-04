package com.ivandsky.puzzlebox.home

class AlsoChainViewModel : ViewModel() {
    private val _state = MutableStateFlow(0)
    val state = _state.also { println(it) }.apply { value = 1 }
}
