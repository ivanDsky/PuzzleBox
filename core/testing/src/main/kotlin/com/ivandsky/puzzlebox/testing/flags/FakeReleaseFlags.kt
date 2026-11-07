package com.ivandsky.puzzlebox.testing.flags

import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** [ReleaseFlags] for tests (ADR-0022): exactly the flags in [on] are enabled; none by default, as in release. */
class FakeReleaseFlags(vararg on: ReleaseFlag) : ReleaseFlags {
    override val enabled: StateFlow<Set<ReleaseFlag>> = MutableStateFlow(on.toSet()).asStateFlow()
}
