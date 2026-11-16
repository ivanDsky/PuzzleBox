package com.ivandsky.puzzlebox.app.di

import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.gameui.PlayTuning
import com.ivandsky.puzzlebox.gameui.PlayTuningSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * The play tuning the app binds (nav-rethink B5, spec A2): the only reader of the BoardErgonomics flag on the play
 * side. It emits [base]'s tuning while the flag is on and null, today's layout, while it is off, so a debug flip
 * applies live and a release build never leaves null until the flag ships on. [scope] is the app scope that keeps
 * the combined value current.
 */
internal class FlaggedPlayTuningSource(
    flags: ReleaseFlags,
    base: PlayTuningSource,
    scope: CoroutineScope,
) : PlayTuningSource {

    override val tuning: StateFlow<PlayTuning?> =
        combine(flags.enabled, base.tuning) { enabled, tuning -> gate(enabled, tuning) }
            .stateIn(scope, SharingStarted.Eagerly, gate(flags.enabled.value, base.tuning.value))

    private companion object {
        fun gate(enabled: Set<ReleaseFlag>, base: PlayTuning?): PlayTuning? =
            base.takeIf { ReleaseFlag.BoardErgonomics in enabled }
    }
}
