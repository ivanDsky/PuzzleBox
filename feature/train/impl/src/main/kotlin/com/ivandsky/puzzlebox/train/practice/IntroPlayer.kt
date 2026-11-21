package com.ivandsky.puzzlebox.train.practice

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The time between two squares landing in the demo's landing step (prototype 02): one reason, so they follow quickly. */
internal const val LandingStaggerMillis = 150L

/**
 * The intro's walk through [entries]: which variant is up ([variant]) and which of its frames ([frame]). Nothing plays
 * by itself: a variant opens on its evidence ([EvidenceFrame]) and [next] shows the landing (the squares landing
 * [LandingStaggerMillis] apart, or all at once under [reduced] motion); past a variant's last frame [next] opens the
 * next variant. [select] opens the one tapped, [seek] shows one frame, [replay] goes back to the variant's evidence.
 */
internal class IntroPlayer(private val entries: List<IntroEntry>, private val reduced: Boolean, private val scope: CoroutineScope) {

    private val mutableVariant = MutableStateFlow(0)
    private val mutableFrame = MutableStateFlow(EvidenceFrame)
    private var job: Job? = null

    /** The index in the entries of the variant shown. */
    val variant: StateFlow<Int> = mutableVariant.asStateFlow()

    /** The frame of that variant shown, from [EvidenceFrame]. */
    val frame: StateFlow<Int> = mutableFrame.asStateFlow()

    /** Opens variant [index] on its evidence. */
    fun select(index: Int) {
        job?.cancel()
        mutableVariant.value = index
        mutableFrame.value = EvidenceFrame
    }

    /** Shows [frame] of the variant. */
    fun seek(frame: Int) {
        job?.cancel()
        mutableFrame.value = frame
    }

    /** Goes back to the evidence of the variant shown. */
    fun replay() = select(mutableVariant.value)

    /** Shows the next step: the landing (its squares one after another), then the next variant's evidence. */
    fun next() {
        val last = entries[mutableVariant.value].frames - 1
        val frame = mutableFrame.value
        when {
            stepOf(frame) < stepOf(last) -> land(from = frame + 1, last = last)
            mutableVariant.value < entries.lastIndex -> select(mutableVariant.value + 1)
        }
    }

    private fun land(from: Int, last: Int) {
        job?.cancel()
        if (reduced) {
            mutableFrame.value = last
            return
        }
        job = scope.launch {
            mutableFrame.value = from
            for (frame in from + 1..last) {
                delay(LandingStaggerMillis)
                mutableFrame.value = frame
            }
        }
    }
}

/** Whether [IntroPlayer.next] has a step to show from [frame] of variant [variant] (with [frames] frames) out of [count] variants. */
internal fun hasNextStep(variant: Int, frame: Int, frames: Int, count: Int): Boolean =
    stepOf(frame) < stepOf(frames - 1) || variant < count - 1
