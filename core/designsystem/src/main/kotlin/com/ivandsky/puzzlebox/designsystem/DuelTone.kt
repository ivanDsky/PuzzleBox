package com.ivandsky.puzzlebox.designsystem

/**
 * Whose colour a duel element wears. [Me] is the primary container pair, [Them] the
 * secondary pair; both come from the theme (ColorTokensTest guards their contrast), so a
 * tone selects roles and never carries a colour itself.
 */
enum class DuelTone { Me, Them }
