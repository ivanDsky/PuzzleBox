package com.ivandsky.puzzlebox.konsist.rules

import com.ivandsky.puzzlebox.konsist.core.SourceFile
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration

/**
 * What P13 and P15 call a flag enum (ADR-0022): the central `ReleaseFlag` in `com.ivandsky.puzzlebox.common.flags`,
 * and every enum, top-level or nested, whose supertypes include `ReleaseFlagKey` or `ReleaseFlag` (plain or qualified),
 * such as an owner's `InsightsFlag`. An enum named `ReleaseFlag` in another package is not one. Matched by name only.
 */
internal object FlagEnums {
    private const val CENTRAL_PACKAGE = "com.ivandsky.puzzlebox.common.flags"
    private const val CENTRAL = "ReleaseFlag"
    private val supertypes = setOf("ReleaseFlagKey", "ReleaseFlag")

    /** The central enum, frozen by the ADR-0022 amendment of 2026-11-18 and since deleted; one that comes back is still judged. */
    fun isCentral(file: SourceFile, declaration: KoClassDeclaration): Boolean =
        declaration.hasEnumModifier && declaration.name == CENTRAL && file.packageName == CENTRAL_PACKAGE

    fun isFlagEnum(file: SourceFile, declaration: KoClassDeclaration): Boolean {
        if (!declaration.hasEnumModifier) return false
        if (isCentral(file, declaration)) return true
        return declaration.parents().any { it.name.substringBefore('<').trim().substringAfterLast('.') in supertypes }
    }
}
