package com.ivandsky.puzzlebox.navigation

/**
 * Process-level "the first screen can be drawn" signal. Home publishes it once its local
 * state has loaded; the app shell publishes it at first composition when the landing screen
 * is not Home. One-way: once ready it stays ready for the life of the process.
 */
class FirstScreenReady {

    @Volatile
    var isReady: Boolean = false
        private set

    fun markReady() {
        isReady = true
    }
}
