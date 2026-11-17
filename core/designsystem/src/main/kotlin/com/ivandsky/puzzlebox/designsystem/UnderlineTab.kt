package com.ivandsky.puzzlebox.designsystem

import androidx.compose.runtime.Composable

/**
 * One tab of [UnderlineTabs]: its [label], an optional [count] after it (nothing for null or 0,
 * "99+" above 99), the [tag] its tap target carries (the count is `<tag>.count`), and a
 * [description] that, when set, is the tab's whole accessibility name (e.g. "Duels, 2 need you").
 * [trailing] is a slot after the count for anything richer, such as the red need-you badge.
 */
data class UnderlineTab(
    val label: String,
    val tag: String,
    val count: Int? = null,
    val description: String? = null,
    val trailing: (@Composable () -> Unit)? = null,
)
