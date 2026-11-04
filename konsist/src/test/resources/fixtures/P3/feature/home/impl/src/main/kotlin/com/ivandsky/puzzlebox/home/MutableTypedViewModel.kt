package com.ivandsky.puzzlebox.home

class MutableTypedViewModel : ViewModel() {
    val x: MutableStateFlow<Int> = MutableStateFlow(0)
}
