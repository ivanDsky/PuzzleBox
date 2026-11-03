package com.ivandsky.puzzlebox.games.tangoui

/**
 * A/B scaffold parameters (design-direction §8): BOTH variants of each open verdict
 * ship behind the debug switcher; verdicts are the user's on hardware (ticket 06),
 * losers deleted then. Marker scale (item 5) is deliberately absent — it is a
 * measurement gate, not a preset.
 */
enum class TangoPlacementVariant {
    /** A11-A: direct tap-cycle `empty → sun → moon → empty`. */
    TapCycle,

    /** A11-B: LinkedIn-faithful two-tap chooser on empty cells; tap on filled clears. */
    TwoTapChooser,
}

enum class TangoViolationStrength {
    /** A/B item 2-A: tint + dashed border + rule-naming caption (guides learning). */
    Captioned,

    /** A/B item 2-B: tint + dashed border, silent (LinkedIn-faithful restraint). */
    Silent,
}

enum class TangoCountIndicators {
    /** A/B item 3: off (LinkedIn-faithful default pending verdict). */
    Off,

    /** A/B item 3: on (understanding aid) — pips or counts in the rails. */
    On,

    /** A/B item 3: absent entirely (no toggle shipped if the verdict kills the aid). */
    Absent,
}

enum class TangoGlyphStyle {
    /** A/B item 4-A: solid fills (LinkedIn craft, §3 values). */
    Solid,

    /** A/B item 4-B: hollow ring outlines with interior shape (Zip dot lineage). */
    Hollow,
}

enum class TangoCellTreatment {
    /** A/B item 6-A: continuous gridline mesh (BoardGrid default, Zip lineage). */
    GridlineMesh,

    /** A/B item 6-B: discrete rounded tiles, [TangoBoardSpec.tileRadiusFraction]. */
    RoundedTiles,
}

/** The full A/B parameter set the debug switcher exposes. */
data class TangoAbVariants(
    val placement: TangoPlacementVariant = TangoPlacementVariant.TapCycle,
    val violationStrength: TangoViolationStrength = TangoViolationStrength.Captioned,
    val countIndicators: TangoCountIndicators = TangoCountIndicators.Off,
    val glyphStyle: TangoGlyphStyle = TangoGlyphStyle.Solid,
    val cellTreatment: TangoCellTreatment = TangoCellTreatment.GridlineMesh,
) {
    companion object {
        /** The two placement variants for the golden matrix (DoD: light+dark × A/B). */
        val bothPlacements = listOf(
            TangoAbVariants(placement = TangoPlacementVariant.TapCycle),
            TangoAbVariants(placement = TangoPlacementVariant.TwoTapChooser),
        )
    }
}
