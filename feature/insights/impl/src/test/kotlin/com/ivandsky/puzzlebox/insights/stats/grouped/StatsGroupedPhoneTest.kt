package com.ivandsky.puzzlebox.insights.stats.grouped

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.insights.domain.recap.SolveRef
import com.ivandsky.puzzlebox.insights.domain.stats.SectionState
import com.ivandsky.puzzlebox.insights.domain.stats.StatsRange
import com.ivandsky.puzzlebox.insights.domain.stats.StatsSection
import com.ivandsky.puzzlebox.insights.domain.stats.VsFriendsStanding
import com.ivandsky.puzzlebox.insights.stats.StatsBody
import com.ivandsky.puzzlebox.insights.stats.StatsFixtures
import com.ivandsky.puzzlebox.insights.stats.StatsScreen
import com.ivandsky.puzzlebox.insights.stats.StatsUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The grouped stats page on a phone (StatsFilters on): four groups with sticky headers, Records split per group, and the filter bar in every state. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class StatsGroupedPhoneTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val opened = mutableListOf<SolveRef>()

    private fun lines(game: com.ivandsky.puzzlebox.model.GameId? = StatsFixtures.zip, filters: Boolean = true) =
        StatsFixtures.readyWithLines(range = StatsRange.Week, game = game).copy(filters = filters, since = StatsFixtures.since)

    private fun show(state: StatsUiState) = rule.setContent {
        PuzzleBoxTheme {
            StatsScreen(
                state = state,
                games = StatsFixtures.games,
                gameName = StatsFixtures::gameName,
                onSelectRange = {},
                onSelectGame = {},
                onOpenSolve = { opened += it },
                onBack = {},
            )
        }
    }

    private fun scrollTo(tag: String) {
        rule.onNodeWithTag("stats.sections").performScrollToNode(hasTestTag(tag))
        rule.onNodeWithTag(tag).assertIsDisplayed()
    }

    @Test
    fun `four groups with their headers and their charts`() {
        show(lines())

        listOf(
            "stats.group.speed" to "stats.trend",
            "stats.group.hintfree" to "stats.hintFree",
            "stats.group.routine" to "stats.weekday",
            "stats.group.friends" to "stats.rankHistory",
        ).forEach { (header, chart) ->
            scrollTo(header)
            scrollTo(chart)
        }
        mapOf("speed" to "Speed", "hintfree" to "Hint-free", "routine" to "Routine", "friends" to "Friends").forEach { (key, title) ->
            rule.onNodeWithTag("stats.sections").performScrollToNode(hasTestTag("stats.group.$key"))
            rule.onNodeWithTag("stats.group.$key").assertTextEquals(title)
        }
    }

    @Test
    fun `records are split per group and headed all time`() {
        show(lines())

        scrollTo("stats.group.records.speed")
        rule.onNodeWithTag("stats.group.records.speed").assertIsDisplayed()
        rule.onNodeWithTag("stats.records.fastest.zip").assertIsDisplayed()
        scrollTo("stats.group.records.hintfree")
        rule.onNodeWithTag("stats.records.hintFree").assertIsDisplayed()
        scrollTo("stats.group.records.routine")
        rule.onNodeWithTag("stats.records.streak").assertIsDisplayed()
        rule.onNodeWithTag("stats.records").assertDoesNotExist()
    }

    @Test
    fun `every records block says all time`() {
        show(lines())

        listOf("speed", "hintfree", "routine").forEach { group ->
            scrollTo("stats.group.records.$group")
            rule.onNodeWithTag("stats.group.records.$group").assert(hasAnyDescendant(hasText("Records · all time")))
        }
    }

    @Test
    fun `a group header sticks to the top of the list under the bar`() {
        show(lines())

        rule.onNodeWithTag("stats.sections").performScrollToNode(hasTestTag("stats.group.routine"))
        rule.onNodeWithTag("stats.sections").performScrollToNode(hasTestTag("stats.group.friends"))
        rule.onNodeWithTag("stats.sections").performTouchInput { swipeUp(startY = bottom * 0.9f, endY = bottom * 0.9f - 400f, durationMillis = 100) }

        val list = rule.onNodeWithTag("stats.sections").getUnclippedBoundsInRoot()
        val tops = listOf("speed", "hintfree", "routine", "friends").mapNotNull { key ->
            rule.onAllNodesWithTag("stats.group.$key").fetchSemanticsNodes().firstOrNull()?.let { rule.onNodeWithTag("stats.group.$key").getUnclippedBoundsInRoot().top }
        }
        assertEquals("a header is stuck at the list's top: $tops vs ${list.top}", true, tops.any { it == list.top })
    }

    @Test
    fun `every group heading after the first carries a hairline on top`() {
        show(lines())

        val speed = rule.onNodeWithTag("stats.group.speed").getUnclippedBoundsInRoot().let { it.bottom - it.top }
        scrollTo("stats.group.hintfree")
        val hintFree = rule.onNodeWithTag("stats.group.hintfree").getUnclippedBoundsInRoot().let { it.bottom - it.top }
        assertEquals("1dp taller than the first heading", 1f, hintFree.value - speed.value, 0.2f)
    }

    @Test
    fun `a record row still opens its solve`() {
        show(lines())

        scrollTo("stats.records.streak")
        rule.onNodeWithTag("stats.records.streak").performClick()

        assertEquals(listOf(StatsFixtures.streakSolve), opened)
    }

    @Test
    fun `any game has no rank history and keeps vs friends`() {
        show(lines(game = null))

        scrollTo("stats.group.friends")
        scrollTo("stats.vsFriends")
        rule.onNodeWithTag("stats.rankHistory").assertDoesNotExist()
    }

    @Test
    fun `locked friends show their lines and no titles`() {
        val locked = SectionState.Ready(VsFriendsStanding.Locked(VsFriendsStanding.Unlock.SignIn))
        show(StatsFixtures.readyWithLines(range = StatsRange.Week, game = null, vsFriends = locked).copy(filters = true, since = StatsFixtures.since))

        scrollTo("stats.vsFriends.locked")
        rule.onNodeWithTag("stats.vsFriends").assertDoesNotExist()
    }

    @Test
    fun `a records section that cannot load says so once, in Speed`() {
        show(lines().let { it.copy(body = StatsBody.Ready((it.body as StatsBody.Ready).sections.map { s -> if (s is StatsSection.Records) StatsSection.Records(SectionState.Unavailable) else s })) })

        scrollTo("stats.group.records.speed.unavailable")
        rule.onAllNodesWithText("Can't load right now").fetchSemanticsNodes().size.let { assertEquals(1, it) }
    }

    @Test
    fun `the filter bar stays in every state`() {
        listOf(
            StatsBody.Loading,
            StatsBody.Restoring,
            StatsBody.Empty,
        ).forEach { body ->
            val state = StatsUiState(StatsRange.Week, null, body, filters = true, since = StatsFixtures.since)
            show(state)
            rule.onNodeWithTag("stats.filters").assertIsDisplayed()
            rule.onNodeWithTag("stats.filter.since").assertIsDisplayed()
            rule.onNodeWithTag("stats.filter.game").assertIsDisplayed()
            rule.activityRule.scenario.recreate()
        }
    }

    @Test
    fun `the filter bar stays over the sections`() {
        show(lines())

        rule.onNodeWithTag("stats.sections").performScrollToNode(hasTestTag("stats.group.friends"))
        rule.onNodeWithTag("stats.filters").assertIsDisplayed()
    }

    @Test
    fun `flag off keeps the flat page`() {
        show(lines(filters = false))

        rule.onNodeWithTag("stats.group.speed").assertDoesNotExist()
        rule.onNodeWithTag("stats.records").assertIsDisplayed()
    }
}
