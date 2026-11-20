package com.ivandsky.puzzlebox.insights.recap

import com.ivandsky.puzzlebox.designsystem.MotionTokens
import kotlin.math.min

/**
 * The friends card's choreography (contract/motion.md `### friends`; tokens Stamp, Pulse, Cascade, Kinetic stay in
 * [MotionTokens] and RecapMotion). Times are ms. The crowd gathers first; "act" times count from the end of the
 * gather, A = [gatherEndMs].
 */
internal object FriendsMotion {
    private const val GatherStepMax = 24
    private const val GatherBudget = 480
    private const val GatherTail = 520

    /** Two beats of the Pulse token, then the crowd parts. */
    const val BeatMs = MotionTokens.PulseDuration
    const val PartAtMs = 720
    const val PartMs = 300
    const val ChipAtMs = 760
    const val ChipFromScale = 0.4f
    const val WobbleMs = 1100
    const val WobbleDp = 4f

    /** Rings of the friends card: x2.5 over 700ms (the Ring token's friends variant). */
    const val RingScale = 2.5f
    const val RingDuration = 700

    /** The points roll: CountHero's friends variant, 1200ms after the usual delay. */
    const val CountDuration = 1_200
    const val CountDelay = 200

    /** The badge stamps as the crowd parts; the name's letters and the points follow it. */
    const val BadgeAtMs = PartAtMs
    const val NameAtMs = 900
    const val PointsAtMs = 1_100
    const val TileAtMs = 120

    /** Ms between one friend's drift-in and the next: min(24, 480 / friends). */
    fun gatherStepMs(friends: Int): Int = min(GatherStepMax, GatherBudget / friends.coerceAtLeast(1))

    /** A = min(friends x 24, 480) + 520: when the gather is over and the beats begin. */
    fun gatherEndMs(friends: Int): Int = min(friends * GatherStepMax, GatherBudget) + GatherTail

    /** When the "Among n friends" chip springs in, from the start of the card: A + 760. */
    fun chipAtMs(friends: Int): Int = gatherEndMs(friends) + ChipAtMs
}
