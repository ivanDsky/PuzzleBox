package com.ivandsky.puzzlebox.train.di

import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Train feature wiring. It binds nothing yet: the technique list's and the snippet round's view models and the progress
 * use cases land in later tickets of the Trainer epic. The Trainer registry (the games that have a trainer) is the app's
 * binding, next to the other game registries, so this module never names a game.
 */
val trainModule: Module = module { }
