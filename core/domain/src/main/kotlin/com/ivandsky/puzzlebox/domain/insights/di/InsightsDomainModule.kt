package com.ivandsky.puzzlebox.domain.insights.di

import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Insights' shared use cases: the period facts and the recap and stats chains over the game history projection. Each
 * lands with its ticket as a line here, so [com.ivandsky.puzzlebox.domain.di.domainModule] stays untouched.
 */
val insightsDomainModule: Module = module { }
