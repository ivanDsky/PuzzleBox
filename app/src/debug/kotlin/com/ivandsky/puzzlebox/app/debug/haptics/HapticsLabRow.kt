package com.ivandsky.puzzlebox.app.debug.haptics

/** One tappable lab row: [id] is what a tester reports back, [play] fires the haptic once. */
internal class HapticsLabRow(val id: String, val play: () -> Unit)

/** Rows that share one way of reaching the motor, under a [title] that says how they play. */
internal class HapticsLabGroup(val title: String, val rows: List<HapticsLabRow>)
