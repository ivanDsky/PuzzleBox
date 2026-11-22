package com.ivandsky.puzzlebox.play.community.shelves

import androidx.compose.runtime.saveable.SaverScope
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.play.community.shelves.seeall.SeeAllScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The open page survives a rotation and process death as text; text it cannot read means the home. */
class ShelvesPageSaverTest {
    private val scope = SaverScope { true }

    private fun roundTrip(page: ShelvesPage?): ShelvesPage? {
        val text = with(ShelvesPageSaver) { scope.save(page) }
        return ShelvesPageSaver.restore(checkNotNull(text))
    }

    @Test
    fun `every page comes back as it was saved`() {
        val pages = listOf(
            null,
            ShelvesPage.Yours,
            ShelvesPage.SeeAll(SeeAllScope.MostPlayed),
            ShelvesPage.SeeAll(SeeAllScope.Newest),
            ShelvesPage.SeeAll(SeeAllScope.Friends),
            ShelvesPage.SeeAll(SeeAllScope.Search),
            ShelvesPage.SeeAll(SeeAllScope.Game(GameId("zip"))),
        )
        pages.forEach { assertEquals(it, roundTrip(it)) }
    }

    @Test
    fun `text it cannot read restores the home`() {
        assertNull(ShelvesPageSaver.restore("somewhere else"))
    }
}
