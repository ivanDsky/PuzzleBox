package com.ivandsky.puzzlebox.app

import org.koin.core.module.Module

/** Release ships only games with a live feed; debug-only games stay out (ADR-0012). */
val buildTypeGameModules: List<Module> = emptyList()
