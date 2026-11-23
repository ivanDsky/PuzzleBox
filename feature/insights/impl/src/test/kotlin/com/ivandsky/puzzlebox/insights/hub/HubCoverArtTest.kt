package com.ivandsky.puzzlebox.insights.hub

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.ivandsky.puzzlebox.designsystem.recap.RecapTileArt
import com.ivandsky.puzzlebox.insights.domain.recap.RecapPeriod
import com.ivandsky.puzzlebox.insights.domain.recap.RecapSummary
import com.ivandsky.puzzlebox.insights.domain.recap.SummarizedPeriod
import com.ivandsky.puzzlebox.model.GameId
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A tile's art is derived once per period, not rebuilt on every recomposition of a scrolling grid. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class HubCoverArtTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun month(solved: Int, game: String = "alpha") = SummarizedPeriod(
        RecapPeriod.Month(YearMonth.of(2026, 10)),
        RecapSummary(solved, listOf(GameId(game) to solved), emptyList()),
    )

    @Test
    fun `a recomposition with the same period hands back the same art`() {
        val item = month(10)
        val seen = mutableListOf<RecapTileArt>()
        var tick by mutableIntStateOf(0)
        rule.setContent {
            tick
            seen += item.art(Color.Black)
        }
        rule.runOnIdle { tick++ }
        rule.waitForIdle()
        assertEquals(2, seen.size)
        assertSame(seen[0], seen[1])
    }

    @Test
    fun `a period with other games gets its own art`() {
        var item by mutableStateOf(month(10))
        val seen = mutableListOf<RecapTileArt>()
        rule.setContent { seen += item.art(Color.Black) }
        rule.runOnIdle { item = month(20, game = "beta") }
        rule.waitForIdle()
        assertNotSame(seen.first(), seen.last())
    }
}
