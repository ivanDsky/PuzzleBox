package com.ivandsky.puzzlebox.app

import org.junit.Test
import org.koin.test.KoinTest
import org.koin.test.verify.verify

class DiGraphTest : KoinTest {

    @Test
    fun `koin module graph is complete`() {
        PuzzleBoxApplication.appModules.forEach { it.verify() }
    }
}
