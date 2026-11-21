package com.ivandsky.puzzlebox.train.practice

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** How long a demo takes to move on to a frame, from the intro handoff (prototype 02): the evidence lights, the first square lands, the rest follow quickly. */
internal object IntroTiming {
    const val EvidenceMillis = 1_300L
    const val LandingMillis = 1_800L
    const val StaggerMillis = 150L

    /** A reduced-motion replay shows one frame for this long, then the next at once. */
    const val ReducedStepMillis = 1_700L

    /** The pause after one variant is done before the next one starts by itself. */
    const val ChainRestMillis = 1_500L

    /** The wait before [frame] (from 1) shows. */
    fun before(frame: Int): Long = when (frame) {
        1 -> EvidenceMillis
        2 -> LandingMillis
        else -> StaggerMillis
    }
}

/**
 * The intro's playback over [entries]: which variant is up ([variant]), which of its frames ([frame]) and whether it is
 * still playing ([playing]). A variant is played once from frame 0 at the handoff's timing and rests on its last frame;
 * the first starts at once and the next follows by itself. [select] plays the one tapped (and none after it), [seek]
 * shows one frame and stops, [replay] plays the variant again. Under [reduced] motion nothing plays by itself: a
 * variant rests on its last frame, and [replay] steps through the frames, [IntroTiming.ReducedStepMillis] each.
 */
internal class IntroPlayer(private val entries: List<IntroEntry>, private val reduced: Boolean, private val scope: CoroutineScope) {

    private val mutableVariant = MutableStateFlow(0)
    private val mutableFrame = MutableStateFlow(0)
    private val mutablePlaying = MutableStateFlow(false)
    private var job: Job? = null

    /** The index in the entries of the variant shown. */
    val variant: StateFlow<Int> = mutableVariant.asStateFlow()

    /** The frame of that variant shown, from 0. */
    val frame: StateFlow<Int> = mutableFrame.asStateFlow()

    val playing: StateFlow<Boolean> = mutablePlaying.asStateFlow()

    init {
        if (reduced) rest(0) else play(0, chain = true, step = IntroTiming::before)
    }

    /** Plays variant [index] (rests on its last frame under reduced motion). */
    fun select(index: Int) = if (reduced) rest(index) else play(index, chain = false, step = IntroTiming::before)

    /** Shows [frame] of the variant and stops. */
    fun seek(frame: Int) {
        job?.cancel()
        mutablePlaying.value = false
        mutableFrame.value = frame
    }

    /** Plays the variant shown again from its first frame. */
    fun replay() {
        val reducedStep: (Int) -> Long = { IntroTiming.ReducedStepMillis }
        val step: (Int) -> Long = if (reduced) reducedStep else IntroTiming::before
        play(mutableVariant.value, chain = false, step = step)
    }

    private fun rest(index: Int) {
        job?.cancel()
        mutableVariant.value = index
        mutableFrame.value = entries[index].frames - 1
        mutablePlaying.value = false
    }

    private fun play(from: Int, chain: Boolean, step: (Int) -> Long) {
        job?.cancel()
        job = scope.launch {
            var index = from
            while (true) {
                mutableVariant.value = index
                mutableFrame.value = 0
                mutablePlaying.value = true
                for (frame in 1 until entries[index].frames) {
                    delay(step(frame))
                    mutableFrame.value = frame
                }
                mutablePlaying.value = false
                if (!chain || index == entries.lastIndex) return@launch
                delay(IntroTiming.ChainRestMillis)
                index++
            }
        }
    }
}
