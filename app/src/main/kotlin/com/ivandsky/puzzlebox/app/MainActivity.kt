package com.ivandsky.puzzlebox.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import com.ivandsky.puzzlebox.data.model.ThemeMode
import com.ivandsky.puzzlebox.data.repository.UserPreferencesRepository
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
class MainActivity : ComponentActivity(), KoinComponent {

    private val preferences: UserPreferencesRepository by inject()

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
                val scope = rememberCoroutineScope()
                PuzzleBoxTheme(darkTheme = mode.isDarkTheme(systemDark)) {
                    PuzzleBoxApp(
                        windowWidthSizeClass = windowSize.widthSizeClass,
                        themeMode = mode,
                        onSetThemeMode = { newMode -> scope.launch { preferences.setThemeMode(newMode) } },
                    )
                }
            }
        }
    }
}

private fun ThemeMode.isDarkTheme(systemDark: Boolean): Boolean = when (this) {
    ThemeMode.DARK -> true
    ThemeMode.LIGHT -> false
    ThemeMode.SYSTEM -> systemDark
}
