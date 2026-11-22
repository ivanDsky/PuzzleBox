package com.ivandsky.puzzlebox.train

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class TrainPhoneListGoldenScreenshots : TrainGoldenScreenshots("phone") {
    @Test fun `list light`() = capture("list", dark = false) { GoldenPage { GoldenTechniqueList(open = false) } }
    @Test fun `list dark`() = capture("list", dark = true) { GoldenPage { GoldenTechniqueList(open = false) } }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_TABLET)
class TrainTabletListGoldenScreenshots : TrainGoldenScreenshots("tablet") {
    @Test fun `list with the placeholder light`() = capture("list", dark = false) { GoldenBeside(open = false) { GoldenPlaceholder() } }
}
