package com.ivandsky.puzzlebox.testing.cloud

import com.ivandsky.puzzlebox.data.cloud.OversizedDrafts
import kotlinx.coroutines.flow.MutableStateFlow

/** Scriptable [OversizedDrafts] for UI tests: tests set `count.value`. */
class FakeOversizedDrafts(initial: Int = 0) : OversizedDrafts {

    override val count: MutableStateFlow<Int> = MutableStateFlow(initial)
}
