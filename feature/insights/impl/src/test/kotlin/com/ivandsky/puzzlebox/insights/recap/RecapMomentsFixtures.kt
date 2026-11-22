package com.ivandsky.puzzlebox.insights.recap

import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard

private val Moments = MomentsFixtures.withoutGames(MomentsFixtures.longest)

/** Every local Moment kind came up, two streak milestones among them. */
internal val RecapFixtures.moments: RecapCard.Moments get() = Moments
