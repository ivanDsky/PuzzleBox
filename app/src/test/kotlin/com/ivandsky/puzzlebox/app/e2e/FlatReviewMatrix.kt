package com.ivandsky.puzzlebox.app.e2e

import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.ivandsky.puzzlebox.navigation.NavigationState
import java.io.File
import org.robolectric.RuntimeEnvironment

/** The two window widths of the review: a phone (bottom bar) and a tablet (rail, two panes). */
internal enum class ReviewWidth(val label: String, private val size: String, private val density: String) {
    Phone("phone", "w411dp-h891dp", "420dpi"),
    Tablet("tablet", "w1280dp-h800dp-land", "hdpi"),
    ;

    /** Robolectric's qualifier order: size, orientation, night, density. */
    fun qualifiers(dark: Boolean): String = "$size-${if (dark) "night" else "notnight"}-$density"
}

/** One cell of the matrix: a width, a theme and the flat switch. */
internal data class ReviewVariant(val width: ReviewWidth, val dark: Boolean, val flat: Boolean) {
    val theme: String get() = if (dark) "dark" else "light"
    val switch: String get() = if (flat) "on" else "off"

    /** `<screen>-<phone|tablet>-<light|dark>-<off|on>.png`. */
    fun fileName(screen: String): String = "$screen-${width.label}-$theme-$switch.png"
}

/**
 * The flat-surfaces review matrix (nav-rethink 17): every variant of one screen, rendered by the real shell and
 * written to `app/build/flat-review/`, a build folder: never a tracked snapshots directory.
 */
internal object FlatReviewMatrix {

    val variants: List<ReviewVariant> = ReviewWidth.entries.flatMap { width ->
        listOf(false, true).flatMap { dark -> listOf(false, true).map { flat -> ReviewVariant(width, dark, flat) } }
    }

    /** `app/build/flat-review`: Gradle runs a module's unit tests with that module as the working directory. */
    val outDir: File = File(System.getProperty("user.dir"), "build/flat-review")

    /**
     * Renders [screen] once per variant: the window's size and night qualifiers set, then the shell started over
     * [state] with the flat switch, then [open] to reach the screen, then the picture. Builds the screen's contact
     * sheet when all variants are in.
     */
    fun render(screen: String, state: () -> NavigationState, open: ShellRobot.() -> Unit) {
        outDir.mkdirs()
        variants.forEach { variant ->
            RuntimeEnvironment.setQualifiers(variant.width.qualifiers(variant.dark))
            shellRobot(state(), darkTheme = variant.dark, flatSurfaces = variant.flat) {
                open()
                settle()
                capture(File(outDir, variant.fileName(screen)))
            }
        }
        FlatReviewContactSheet.build(screen, outDir)
    }

    /** Past every enter animation of the screen just opened. */
    private fun ShellRobot.settle() {
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(SETTLE_MS)
        rule.waitForIdle()
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(file: File) = captureScreenRoboImage(file.path)

    private const val SETTLE_MS = 800L
}
