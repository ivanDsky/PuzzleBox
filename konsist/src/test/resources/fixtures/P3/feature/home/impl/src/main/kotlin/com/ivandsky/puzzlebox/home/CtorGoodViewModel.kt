package com.ivandsky.puzzlebox.home

class CtorGoodViewModel(
    private val state: MutableStateFlow<Int>,
    val title: String,
    repo: Repo,
) : ViewModel() {
    val view = state.asStateFlow()
    val mapped = state.also { }.map { it + 1 }
}
