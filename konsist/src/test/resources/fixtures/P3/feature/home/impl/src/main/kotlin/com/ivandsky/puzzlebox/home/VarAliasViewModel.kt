package com.ivandsky.puzzlebox.home

class VarAliasViewModel : ViewModel() {
    private var _items: List<Int> = emptyList()
    val items get() = _items
}
