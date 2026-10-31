package com.ivandsky.puzzlebox.app

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.ivandsky.puzzlebox.app.nav.Home
import com.ivandsky.puzzlebox.app.nav.Play
import com.ivandsky.puzzlebox.app.nav.Profile
import com.ivandsky.puzzlebox.app.nav.topLevelDestinations
import com.ivandsky.puzzlebox.app.screens.HomeScreen
import com.ivandsky.puzzlebox.app.screens.PlayScreen
import com.ivandsky.puzzlebox.app.screens.ProfileScreen

@Composable
fun PuzzleBoxApp(modifier: Modifier = Modifier) {
    val backStack = rememberNavBackStack(Home)

    Scaffold(
        modifier = modifier,
        bottomBar = {
            NavigationBar {
                topLevelDestinations.forEach { destination ->
                    val selected = backStack.lastOrNull() == destination.key
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (!selected) {
                                backStack.clear()
                                backStack.add(destination.key)
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavDisplay(
            backStack = backStack,
            modifier = Modifier.padding(innerPadding),
            entryProvider = entryProvider {
                entry<Home> { HomeScreen() }
                entry<Play> { PlayScreen() }
                entry<Profile> { ProfileScreen() }
            },
        )
    }
}
