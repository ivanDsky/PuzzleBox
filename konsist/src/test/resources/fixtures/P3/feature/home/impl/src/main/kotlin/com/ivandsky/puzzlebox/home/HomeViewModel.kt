package com.ivandsky.puzzlebox.home

class HomeViewModel : ViewModel() {
    private val _state = MutableStateFlow(0)
    val state: StateFlow<Int> = _state
    private val _x = MutableStateFlow(0)
    val typed: StateFlow<Int> = _x
    val ro = _x.asStateFlow()

    companion object {
        var counter = 0
    }

    class Nested {
        var scratch = 0
    }
}

data class HomeUiState(var count: Int)
