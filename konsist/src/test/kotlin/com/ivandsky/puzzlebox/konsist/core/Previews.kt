package com.ivandsky.puzzlebox.konsist.core

import com.lemonappdev.konsist.api.container.KoScope
import com.lemonappdev.konsist.api.declaration.KoFunctionDeclaration

/** The one definition of "a preview function", shared by S1 and S4. */
object Previews {
    /** `Preview` and the multipreviews `androidx.compose.ui.tooling.preview` ships, which are not in the scanned source. */
    private val LIBRARY = setOf("Preview", "PreviewLightDark", "PreviewFontScale", "PreviewScreenSizes", "PreviewDynamicColors")

    /** The library names plus every annotation class in [scope] carrying one, transitively (multipreviews of multipreviews). */
    fun annotationNames(scope: KoScope): Set<String> {
        val annotationClasses = scope.classes().filter { it.hasAnnotationModifier }
        val known = LIBRARY.toMutableSet()
        do {
            val grew = annotationClasses
                .filter { cls -> cls.annotations.any { it.name in known } }
                .any { known.add(it.name) }
        } while (grew)
        return known
    }

    fun isPreview(function: KoFunctionDeclaration, annotationNames: Set<String>): Boolean =
        function.annotations.any { it.name in annotationNames }
}
