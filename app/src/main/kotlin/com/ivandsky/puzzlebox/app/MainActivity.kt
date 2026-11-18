package com.ivandsky.puzzlebox.app

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.ivandsky.puzzlebox.app.nav.FriendLink
import com.ivandsky.puzzlebox.app.nav.SharedText
import com.ivandsky.puzzlebox.data.repository.SharedTextStore
import com.ivandsky.puzzlebox.data.repository.UserPreferencesRepository
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.model.ThemeMode
import com.ivandsky.puzzlebox.model.ThemeRead
import com.ivandsky.puzzlebox.navigation.FirstScreenReady
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * The single activity (singleTask): a tapped share link starts it or, when it is already
 * running, arrives as a new intent; either way the link waits in [pendingLink] until the app
 * has put it on the back stack. A plain-text share takes the same two doors:
 * its text goes to the [SharedTextStore] and [pendingShare] has the app show the Community tab,
 * whose Yours list imports it. A recreated activity restores its back stack and never re-reads the
 * intent that started it, so the friend's card is pushed and the text imported once.
 */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
class MainActivity : ComponentActivity(), KoinComponent {

    private val preferences: UserPreferencesRepository by inject()

    private val sharedText: SharedTextStore by inject()

    private val firstScreen: FirstScreenReady by inject()

    // Built in onCreate, on the clock the splash's one-second cap is measured on.
    private lateinit var splashPolicy: SplashReleasePolicy

    // The persisted theme once read; null until then, so nothing composes before it.
    private var theme by mutableStateOf<ThemeRead?>(null)

    private var pendingLink by mutableStateOf<String?>(null)

    // A share is waiting in the store and the navigator has yet to open Custom for it.
    private var pendingShare by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Installed before super so the system splash is the one the window opens with; it stays
        // up until the release policy says the first screen is ready.
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splashPolicy = SplashReleasePolicy(firstScreen, nowMillis = SystemClock::elapsedRealtime)
        splash.setKeepOnScreenCondition { splashPolicy.keepSplash() }
        lifecycleScope.launch {
            preferences.themeRead.collect { read ->
                if (read.failed) splashPolicy.onLocalReadFailed()
                theme = read
            }
        }
        val fresh = savedInstanceState == null
        pendingLink = if (fresh) {
            intent.friendLinkUri()
        } else {
            savedInstanceState?.getString(PENDING_LINK_KEY)
        }
        pendingShare = if (fresh) {
            handOffSharedText(intent)
        } else {
            savedInstanceState?.getBoolean(PENDING_SHARE_KEY) == true
        }
        enableEdgeToEdge()
        setContent {
            // Nothing composes before the persisted theme is read, so a stored dark theme never
            // flashes light; the system splash covers that wait.
            val systemDark = isSystemInDarkTheme()
            val windowSize = calculateWindowSizeClass(this)

            theme?.let { read ->
                val darkTheme = read.mode.isDarkTheme(systemDark)

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

                PuzzleBoxTheme(
                    darkTheme = darkTheme,
                ) {
                    PuzzleBoxApp(
                        windowWidthSizeClass = windowSize.widthSizeClass,
                        pendingLink = pendingLink,
                        onLinkApplied = { pendingLink = null },
                        pendingShare = pendingShare,
                        onShareApplied = { pendingShare = false },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.friendLinkUri()?.let { pendingLink = it }
        if (handOffSharedText(intent)) pendingShare = true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        pendingLink?.let { outState.putString(PENDING_LINK_KEY, it) }
        outState.putBoolean(PENDING_SHARE_KEY, pendingShare)
    }

    /**
     * Puts the text [intent] shares in the [SharedTextStore]; the Yours list on the Community tab
     * takes it once it is shown. True when text was put.
     */
    private fun handOffSharedText(intent: Intent): Boolean {
        val text = intent.sharedText() ?: return false
        sharedText.put(text)
        return true
    }
}

private const val PENDING_LINK_KEY = "puzzlebox.pendingLink"
private const val PENDING_SHARE_KEY = "puzzlebox.pendingShare"

// A task relaunched from Recents re-delivers the intent that started it: its link or share was
// handled then.
private val Intent.isRelaunchedFromHistory: Boolean
    get() = flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0

private fun Intent.friendLinkUri(): String? = dataString?.takeIf { uri ->
    action == Intent.ACTION_VIEW && !isRelaunchedFromHistory && FriendLink.of(uri) != null
}

private fun Intent.sharedText(): String? =
    SharedText.of(action, type, getCharSequenceExtra(Intent.EXTRA_TEXT)).takeUnless { isRelaunchedFromHistory }

private fun ThemeMode.isDarkTheme(systemDark: Boolean): Boolean = when (this) {
    ThemeMode.SYSTEM -> systemDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}
