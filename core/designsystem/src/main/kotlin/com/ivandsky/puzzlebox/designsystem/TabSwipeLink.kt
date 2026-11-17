package com.ivandsky.puzzlebox.designsystem

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Prototype 141 (throwaway): what a tab pager tells the rest of its screen about a page change in flight, so a right
 * pane or a head action moves in step with the pages instead of after they settle. The screen creates one, provides it
 * through [LocalTabSwipeLink] and reads it; the pager below attaches to it (only the prototype options do, so
 * [attached] is false under [TabSwipeOption.Today] and the screen keeps its shipped code).
 *
 * While a finger drags or a swipe settles, [under] and [over] are the two pages on show and [overShare] how far the
 * pager is into [over]. While a tap moves to another page they are the page left and the page headed for, however many
 * lie between: [overShare] is then the share of the slide done (a glide), or runs 0 to 1 on the pane's own clock (a
 * fade through). At rest [over] is [under]. Every value is snapshot state: read [overShare] and [shown] in a draw or
 * layer block, and the page numbers through `derivedStateOf`.
 */
@Stable
class TabSwipeLink {
    private var position: (() -> Float)? by mutableStateOf(null)
    private var jumpFrom by mutableIntStateOf(NONE)
    private var jumpTo by mutableIntStateOf(NONE)
    private var jumpShare by mutableFloatStateOf(0f)
    private var gliding by mutableStateOf(false)

    /** True while a prototype pager drives this link. */
    val attached: Boolean get() = position != null

    /** The page drawn beneath. */
    val under: Int get() = if (jumpTo != NONE) jumpFrom else floor(at()).toInt()

    /** The page drawn on top at [overShare]; [under] itself at rest. */
    val over: Int get() = if (jumpTo != NONE) jumpTo else ceil(at()).toInt()

    /** How much of [over] shows, 0 to 1. */
    val overShare: Float
        get() = when {
            jumpTo == NONE -> at() - floor(at())
            gliding -> ((at() - jumpFrom) / (jumpTo - jumpFrom)).coerceIn(0f, 1f)
            else -> jumpShare
        }

    /** How much of [page] shows, 0 to 1: what a head action that belongs to one page fades by. */
    fun shown(page: Int): Float = when {
        jumpTo == NONE -> (1f - abs(at() - page)).coerceIn(0f, 1f)
        page == jumpTo -> overShare
        page == jumpFrom -> 1f - overShare
        else -> 0f
    }

    private fun at(): Float = position?.invoke() ?: 0f

    internal fun attach(position: () -> Float) {
        this.position = position
    }

    internal fun detach() {
        position = null
    }

    /** A tap's slide from [from] to [to]: the pane goes straight from one to the other by the share of the way done. */
    internal fun glide(from: Int, to: Int) {
        if (from == to) return
        gliding = true
        jumpFrom = from
        jumpTo = to
    }

    internal fun jump(from: Int, to: Int) {
        gliding = false
        jumpShare = 0f
        jumpFrom = from
        jumpTo = to
    }

    internal fun jumpShare(share: Float) {
        jumpShare = share
    }

    internal fun land() {
        jumpFrom = NONE
        jumpTo = NONE
        gliding = false
    }

    private companion object {
        const val NONE = -1
    }
}

/** The [TabSwipeLink] of the screen a tab pager sits in; null where nothing follows the pager. */
val LocalTabSwipeLink = staticCompositionLocalOf<TabSwipeLink?> { null }
