package com.ivandsky.puzzlebox.app.widget

/** Records every state it was switched to; [failure] makes it throw like a package manager that refuses. */
class FakeWidgetComponentSwitch(private val failure: Exception? = null) : WidgetComponentSwitch {
    val switched = mutableListOf<Boolean>()

    override fun setEnabled(enabled: Boolean) {
        switched += enabled
        failure?.let { throw it }
    }
}
