package com.ivandsky.puzzlebox.app

import android.content.Intent
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.ivandsky.puzzlebox.app.nav.FriendLink
import com.ivandsky.puzzlebox.data.repository.UserPreferencesRepository
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.model.ThemeMode
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * The single activity (singleTask): a tapped share link starts it or, when it is already
 * running, arrives as a new intent; either way the link waits in [pendingLink] until the app
 * has put it on the back stack. A recreated activity restores its back stack and never
 * re-reads the intent that started it, so the friend's card is pushed once.
 */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
class MainActivity : ComponentActivity(), KoinComponent {

    private val preferences: UserPreferencesRepository by inject()

    private var pendingLink by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingLink = if (savedInstanceState == null) {
            intent.friendLinkUri()
        } else {
            savedInstanceState.getString(PENDING_LINK_KEY)
        }
        enableEdgeToEdge()
        setContent {
            // Hold the composition until the persisted theme arrives so a stored dark
            // theme never flashes light for a frame.
            val themeMode by preferences.themeMode.collectAsState(initial = null)
            val systemDark = isSystemInDarkTheme()
            val windowSize = calculateWindowSizeClass(this)

            themeMode?.let { mode ->
                val darkTheme = mode.isDarkTheme(systemDark)

                // enableEdgeToEdge() above follows the SYSTEM dark mode, but the app can
                // force the opposite, so the system bars must follow the RESOLVED
                // in-app theme: light appearance = dark icons (light theme), and the
                // inverse for dark. SideEffect re-applies whenever the flag flips,
                // including the SYSTEM mode following an OS theme change.
                val view = LocalView.current
                SideEffect {
                    WindowCompat.getInsetsController(window, view).apply {
                        isAppearanceLightStatusBars = !darkTheme
                        isAppearanceLightNavigationBars = !darkTheme
                    }
                }

                PuzzleBoxTheme(darkTheme = darkTheme) {
                    PuzzleBoxApp(
                        windowWidthSizeClass = windowSize.widthSizeClass,
                        pendingLink = pendingLink,
                        onLinkApplied = { pendingLink = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.friendLinkUri()?.let { pendingLink = it }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        pendingLink?.let { outState.putString(PENDING_LINK_KEY, it) }
    }
}

private const val PENDING_LINK_KEY = "puzzlebox.pendingLink"

// A task relaunched from Recents replays the intent that started it: its link was handled then.
private fun Intent.friendLinkUri(): String? = dataString?.takeIf { uri ->
    action == Intent.ACTION_VIEW &&
        flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0 &&
        FriendLink.of(uri) != null
}

private fun ThemeMode.isDarkTheme(systemDark: Boolean): Boolean = when (this) {
    ThemeMode.SYSTEM -> systemDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}
