package com.ivandsky.puzzlebox.designsystem

import androidx.compose.animation.core.AnimationVector
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.VectorizedFiniteAnimationSpec

/**
 * [spec], except that a run which starts on its target takes no time.
 *
 * A pager's fling behaviour also runs when a list inside the page finishes its own fling: every list fling ends by
 * handing its leftover velocity up the nested-scroll chain, and the pager settles on it. With a page at rest that
 * settle goes from where it is to where it is, but a tween still waits out its whole length, and the list's overscroll
 * (its stretch at an edge) is applied only after the chain returns. So a list that landed on its edge sat still for the
 * settle and then bounced, with nobody touching it. A settle that has somewhere to go keeps [spec]'s own length.
 */
internal class RestingSettle(private val spec: FiniteAnimationSpec<Float>) : FiniteAnimationSpec<Float> {

    override fun <V : AnimationVector> vectorize(converter: TwoWayConverter<Float, V>): VectorizedFiniteAnimationSpec<V> {
        val inner = spec.vectorize(converter)
        return object : VectorizedFiniteAnimationSpec<V> by inner {
            override fun getDurationNanos(initialValue: V, targetValue: V, initialVelocity: V): Long =
                if (initialValue == targetValue) 0L else inner.getDurationNanos(initialValue, targetValue, initialVelocity)
        }
    }

    override fun equals(other: Any?): Boolean = other is RestingSettle && other.spec == spec

    override fun hashCode(): Int = spec.hashCode()
}
