package com.ivandsky.puzzlebox.app.widget

/** Counts the enables; [failure] makes it throw like a package manager that refuses. */
class FakeWidgetComponentSwitch(private val failure: Exception? = null) : WidgetComponentSwitch {
    var enables = 0
        private set

    override fun enable() {
        enables++
        failure?.let { throw it }
    }
}
