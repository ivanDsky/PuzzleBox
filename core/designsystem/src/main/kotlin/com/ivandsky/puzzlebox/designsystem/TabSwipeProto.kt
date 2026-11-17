package com.ivandsky.puzzlebox.designsystem

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Prototype 141 (throwaway): the process-wide switch every [UnderlineTabPager] reads. [option] is the page-change
 * behaviour on show; [switcher] draws the chips that change it over the pager, and only a debuggable build turns it on.
 * Both reset when the process dies.
 */
object TabSwipeProto {
    var option by mutableStateOf(TabSwipeOption.Today)
    var switcher by mutableStateOf(false)
}
