package com.ivandsky.puzzlebox.konsist.core

enum class SourceSetKind {
    PRODUCTION,
    TEST,
    ;

    companion object {
        fun of(sourceSetName: String): SourceSetKind =
            if (sourceSetName.startsWith("test") ||
                sourceSetName.startsWith("androidTest") ||
                sourceSetName.endsWith("Test")
            ) {
                TEST
            } else {
                PRODUCTION
            }
    }
}
