package com.ivandsky.puzzlebox.games.sample

import kotlinx.coroutines.CoroutineDispatcher

class TemplateBad(val d: CoroutineDispatcher) {
    val name = "on ${kotlinx.coroutines.Dispatchers.IO}"
}
