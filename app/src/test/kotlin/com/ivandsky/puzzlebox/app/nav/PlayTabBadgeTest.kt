package com.ivandsky.puzzlebox.app.nav

import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.play.api.PlayKey
import org.junit.Test
import org.junit.Assert.assertEquals

class PlayTabBadgeTest {
    @Test
    fun badgeShowsWhenAnythingIsInProgress() {
        assertEquals(setOf(PlayKey), playTabBadged(anyInProgress = true, duelsNeedYou = 0))
    }

    @Test
    fun badgeShowsWhenDuelsNeedYou() {
        assertEquals(setOf(PlayKey), playTabBadged(anyInProgress = false, duelsNeedYou = 2))
    }

    @Test
    fun badgeIsEmptyWhenNeitherApplies() {
        assertEquals(emptySet<NavKey>(), playTabBadged(anyInProgress = false, duelsNeedYou = 0))
    }

    @Test
    fun bothTogetherStillGiveASinglePlayKey() {
        assertEquals(setOf(PlayKey), playTabBadged(anyInProgress = true, duelsNeedYou = 3))
    }
}
