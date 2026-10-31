package com.ivandsky.puzzlebox.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.ivandsky.puzzlebox.data.model.ThemeMode
import com.ivandsky.puzzlebox.data.repository.DebugStyleRepository
import com.ivandsky.puzzlebox.data.repository.UserPreferencesRepository
import com.ivandsky.puzzlebox.designsystem.PaletteVariant
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.TypographyVariant
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
class MainActivity : ComponentActivity(), KoinComponent {

    private val preferences: UserPreferencesRepository by inject()

    // Debug-only (ticket 08). Lazy Koin resolution: a release build never touches this
    // property, so the debug DataStore file is never created there.
    private val debugStyleRepository: DebugStyleRepository by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Hold the composition until the persisted theme arrives so a stored dark
            // theme never flashes light for a frame.
            val themeMode by preferences.themeMode.collectAsState(initial = null)
            val systemDark = isSystemInDarkTheme()
            val windowSize = calculateWindowSizeClass(this)

            themeMode?.let { mode ->
                val darkTheme = mode.isDarkTheme(systemDark)

                // Debug style prototypes (ticket 08): null in release, so the theme
                // resolves the shipped default variants and no switcher exists.
                val styleControls = rememberDebugStyleControls()
                val typographyVariant = styleControls?.typography ?: TypographyVariant.DEFAULT
                val paletteVariant = styleControls?.palette ?: PaletteVariant.DEFAULT

                // enableEdgeToEdge() above follows the SYSTEM dark mode, but the app can
                // force the opposite, so the system bars must follow the RESOLVED
                // in-app theme: light appearance = dark icons (light theme), and the
                // inverse for dark. Palette variants pin their look (C2-C5 always
                // light, C6/C7 always dark; ticket 09), so the icons follow the
                // resolved scheme, not the raw theme request. SideEffect re-applies
                // whenever the resolved flag flips, including the SYSTEM mode
                // following an OS theme change.
                val effectiveDark = paletteVariant.resolvesDark(darkTheme)
                val view = LocalView.current
                SideEffect {
                    WindowCompat.getInsetsController(window, view).apply {
                        isAppearanceLightStatusBars = !effectiveDark
                        isAppearanceLightNavigationBars = !effectiveDark
                    }
                }

                val scope = rememberCoroutineScope()
                PuzzleBoxTheme(
                    darkTheme = darkTheme,
                    typographyVariant = typographyVariant,
                    paletteVariant = paletteVariant,
                ) {
                    PuzzleBoxApp(
                        windowWidthSizeClass = windowSize.widthSizeClass,
                        themeMode = mode,
                        onSetThemeMode = { newMode -> scope.launch { preferences.setThemeMode(newMode) } },
                        styleControls = styleControls,
                    )
                }
            }
        }
    }

    /**
     * Resolves the persisted debug style selection; null (and zero debug-store reads)
     * in release, where BuildConfig.DEBUG is false. Writes persist per axis so the
     * pick survives process death.
     */
    @Composable
    private fun rememberDebugStyleControls(): DebugStyleControls? {
        if (!BuildConfig.DEBUG) return null
        val typographyName by debugStyleRepository.typographyVariantName.collectAsState(initial = null)
        val paletteName by debugStyleRepository.paletteVariantName.collectAsState(initial = null)
        val scope = rememberCoroutineScope()
        return DebugStyleControls(
            typography = TypographyVariant.fromStored(typographyName) ?: TypographyVariant.DEFAULT,
            palette = PaletteVariant.fromStored(paletteName) ?: PaletteVariant.DEFAULT,
            onTypography = { variant -> scope.launch { debugStyleRepository.setTypographyVariantName(variant.name) } },
            onPalette = { variant -> scope.launch { debugStyleRepository.setPaletteVariantName(variant.name) } },
        )
    }
}

private fun ThemeMode.isDarkTheme(systemDark: Boolean): Boolean = when (this) {
    ThemeMode.DARK -> true
    ThemeMode.LIGHT -> false
    ThemeMode.SYSTEM -> systemDark
}
