package com.ivandsky.puzzlebox.home

class GoodChildViewModel : BaseViewModel() {
    private val _state = MutableStateFlow(0)
    val state: StateFlow<Int> = _state
}
