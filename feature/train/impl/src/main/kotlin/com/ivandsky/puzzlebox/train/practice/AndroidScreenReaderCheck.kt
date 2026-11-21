package com.ivandsky.puzzlebox.train.practice

import android.content.Context
import android.view.accessibility.AccessibilityManager

/** The platform's touch-exploration switch: the one TalkBack and the other screen readers turn on. */
internal class AndroidScreenReaderCheck(context: Context) : ScreenReaderCheck {
    private val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager

    override fun on(): Boolean = manager.isTouchExplorationEnabled
}
