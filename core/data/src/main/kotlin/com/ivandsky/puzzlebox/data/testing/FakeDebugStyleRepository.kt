package com.ivandsky.puzzlebox.data.testing

import com.ivandsky.puzzlebox.data.repository.DebugStyleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Handwritten fake for feature/app tests: in-memory variant names, recorded writes. */
class FakeDebugStyleRepository(
    initialTypographyName: String? = null,
    initialPaletteName: String? = null,
) : DebugStyleRepository {

    private val typographyNames = MutableStateFlow(initialTypographyName)
    private val paletteNames = MutableStateFlow(initialPaletteName)

    val setTypographyCalls = mutableListOf<String>()
    val setPaletteCalls = mutableListOf<String>()

    override val typographyVariantName: Flow<String?> = typographyNames.asStateFlow()
    override val paletteVariantName: Flow<String?> = paletteNames.asStateFlow()

    override suspend fun setTypographyVariantName(name: String) {
        setTypographyCalls += name
        typographyNames.value = name
    }

    override suspend fun setPaletteVariantName(name: String) {
        setPaletteCalls += name
        paletteNames.value = name
    }
}
