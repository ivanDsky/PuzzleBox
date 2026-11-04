package com.ivandsky.puzzlebox.home

class HomeViewModel : ViewModel() {
    private val _state = MutableStateFlow(0)
    val state: StateFlow<Int> = _state

    companion object {
        var counter = 0
    }

    class Nested {
        var scratch = 0
    }
}

data class HomeUiState(var count: Int)
