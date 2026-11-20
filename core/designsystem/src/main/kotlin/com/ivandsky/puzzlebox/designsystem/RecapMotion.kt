package com.ivandsky.puzzlebox.designsystem

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The recap story's motion tokens (contract/motion.md tokens 11-24), values from the frozen contract. Tokens 1-10
 * reuse [MotionTokens]. Reduced motion collapses every one of them to the final state under a
 * [MotionTokens.ReducedFadeDuration] crossfade.
 */
object RecapMotion {
    // CountHero: the big figure rolls CountHeroDuration after CountHeroDelay.
    const val CountHeroDuration = 1000
    const val CountHeroDelay = 200

    // Odometer: columns stagger OdometerStagger left first; the ones column laps past its target
    // twice, tens and hundreds once (OdometerLaps by place, ones first), every other column not at all.
    const val OdometerStagger = 60
    val OdometerLaps: List<Int> = listOf(2, 1, 1)
    const val OdometerLapsBeyond = 0

    // DeckSpring: the recap deck's spring for deal, throw promote, flips and replay.
    const val DeckSpringDamping = 0.7f
    const val DeckSpringStiffness = 600f

    // Deal: a card arrives from DealRise below, DealTilt degrees and DealScaleFrom scale.
    const val DealDuration = 420
    val DealRise: Dp = 60.dp
    const val DealTilt = -4f
    const val DealScaleFrom = 0.9f

    // Throw: the top card leaves ThrowTravel card widths with ThrowTilt degrees; back takes ThrowBackDuration.
    const val ThrowDuration = 180
    const val ThrowTravel = -1.25f
    const val ThrowTilt = -10f
    const val ThrowBackDuration = 280

    // Flip: the dealt card turns FlipTurn degrees face-up FlipDelay ms into the deal, on DeckSpring.
    const val FlipDelay = 300
    const val FlipTurn = 180f

    // Drag: a dragged card tilts DragTilt degrees per card width of travel.
    const val DragTilt = 10f

    // Hold: a press longer than HoldThreshold ms slides the top card HoldPeek of its height down.
    const val HoldThreshold = 280
    const val HoldPeek = 0.74f

    // DeckStack: cards peek below the top one, each step lower and smaller; promote and hold reuse the Nav clocks.
    val DeckStackStep: Dp = 14.dp
    const val DeckStackScaleStep = 0.045f
    const val DeckStackVisible = 3
    const val DeckStackPromote = MotionTokens.NavFullSlideEnter
    const val DeckStackHoldIn = MotionTokens.NavFullSlideEnter
    const val DeckStackHoldOut = MotionTokens.NavFullSlidePop

    // Sheen: 1.4 × SweepDuration, at SheenAlpha, on EasingStandard, SheenDelay ms after the flip starts.
    const val SheenDuration = MotionTokens.SweepDuration * 14 / 10
    const val SheenAlpha = 0.6f
    const val SheenDelay = 250

    // Kinetic: a letter rises KineticRise of its height on the Stamp spring; the per-letter stagger is
    // clamp(KineticStaggerBudget / letters, KineticStaggerMin, KineticStaggerMax).
    const val KineticRise = 1.12f
    const val KineticStaggerBudget = 620
    const val KineticStaggerMin = 8
    const val KineticStaggerMax = 26

    // Cascade: rows pop in CascadeRowStep apart, the last starting by CascadeRowCap, on DeckSpring from a flipped, small start.
    const val CascadeRowStep = 24
    const val CascadeRowCap = 480
    const val CascadeFromScale = 0.6f
    const val CascadeFromRotationX = 90f

    // Sweep: a run of n days lights over min(SweepRunMax, SweepRunBase + SweepRunPerDay × n) ms; from
    // SweepFewDays days on it is the max. The curve is EasingSweep (ease-out cubic).
    const val SweepRunBase = 400
    const val SweepRunPerDay = 120
    const val SweepRunMax = 1500
    const val SweepFewDays = 8
    val EasingSweep = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f)

    // Slam: the best time climbs, holds, the ghost peels, then the figure drops with an overshoot and recoils.
    const val SlamClimb = 560
    const val SlamHold = 80
    const val SlamPeel = 420
    const val SlamDrop = 300
    const val SlamOvershoot = 0.35f
    const val SlamLandingPause = 480
    val SlamRecoil: Dp = 6.dp
    val SlamRecoilBack: Dp = (-3).dp

    // Sticker: stamps in on the Stamp spring from StickerFromScale, dropping from StickerDrop above and
    // tilting StickerTilt degrees; step is min(StickerStepMax, StickerStepBudget / n) ms.
    const val StickerFromScale = 1.9f
    val StickerDrop: Dp = 34.dp
    const val StickerTilt = -18f
    const val StickerStepMax = 110
    const val StickerStepBudget = 1200

    // Ring: an expanding ring grows RingScale over RingDuration while its alpha runs RingAlphaFrom to 0.
    const val RingScale = 2.0f
    const val RingDuration = 560
    const val RingAlphaFrom = 0.7f
    const val RingStagger = 140

    // Peel: a sticker lifts PeelLift, tilts PeelTilt degrees and scales to PeelScale over PeelDuration on EasingStandard.
    val PeelLift: Dp = 5.dp
    const val PeelTilt = 5f
    const val PeelScale = 1.08f
    const val PeelDuration = 240
    const val PeelCurl = 120

    // Yearflick: the twelve month squares of a month card flip in YearSquareStep ms apart on DeckSpring; its count rolls
    // for MotionTokens.ScoreCountUpDuration.
    const val YearSquareStep = 24
    const val YearSquareFromTurn = 90f

    // Yearposter: month tile i springs in on DeckSpring from YearTileFrom, YearTileTilt degrees and YearTileFromScale
    // at YearTileStart + i × YearTileStep ms; the chips then stamp in YearChipStep apart.
    const val YearTileStart = 160
    const val YearTileStep = 40
    val YearTileFromX: Dp = (-150).dp
    val YearTileFromY: Dp = 40.dp
    const val YearTileTilt = -14f
    const val YearTileFromScale = 2.4f
    const val YearChipStep = 80

    // Confetti: ConfettiPieces fly once, starting ConfettiDelay ms after the last tile starts, each at an initial speed of
    // ConfettiSpeedMin..ConfettiSpeedMax dp/s under ConfettiGravity dp/s², fading over ConfettiFade ms.
    const val ConfettiPieces = 16
    const val ConfettiDelay = 700
    const val ConfettiSpeedMin = 190f
    const val ConfettiSpeedMax = 325f
    const val ConfettiGravity = 520f
    const val ConfettiFade = 1100
}
