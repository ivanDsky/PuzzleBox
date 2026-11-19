package com.ivandsky.puzzlebox.play.random

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.editor.Difficulty.Level
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.play.community.ALPHA
import com.ivandsky.puzzlebox.play.community.BETA
import com.ivandsky.puzzlebox.play.community.CommunityFixture
import com.ivandsky.puzzlebox.play.community.HOUR
import com.ivandsky.puzzlebox.play.community.NOW
import com.ivandsky.puzzlebox.play.domain.ListCommunityPuzzles
import com.ivandsky.puzzlebox.play.domain.LoadPastDay
import com.ivandsky.puzzlebox.play.domain.random.LoadRandomDay
import com.ivandsky.puzzlebox.play.domain.random.LoadRandomPuzzle
import com.ivandsky.puzzlebox.play.domain.random.LoadRandomShared
import com.ivandsky.puzzlebox.play.domain.random.RandomPick
import com.ivandsky.puzzlebox.play.testDispatchers
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import kotlin.random.Random
import kotlinx.coroutines.runBlocking

/**
 * The real Random sheet over the real view model, for the chip tests: New today has alpha Easy (AAA111)
 * and Hard (BBB222) and beta Easy (CCC333); Past puzzles also offers gamma. The view model's preset is alpha.
 */
internal class RandomFilterSheetFixture(private val rule: ComposeContentTestRule) {
    private val community = CommunityFixture().also {
        runBlocking {
            it.publish("AAA111", NOW - HOUR, ALPHA, Level.Easy)
            it.publish("BBB222", NOW - 2 * HOUR, ALPHA, Level.Hard)
            it.publish("CCC333", NOW - 3 * HOUR, BETA, Level.Easy)
        }
    }
    private val gamma = GameId("gamma")
    private fun gameOf(id: GameId) = RandomGame(id, id.value.replaceFirstChar(Char::uppercase), GameBrand.Neutral)
    val communityGames = listOf(ALPHA, BETA).map(::gameOf)
    val pastGames = communityGames + gameOf(gamma)
    val opened = mutableListOf<RandomPick>()
    var dismissed = 0
        private set
    lateinit var viewModel: RandomPuzzleViewModel
        private set

    fun mountSheet(fontScale: Float = 1f, restoration: StateRestorationTester? = null) {
        val daily = FakeDailyRepository()
        val registry = TestGames.registry(listOf(ALPHA, BETA))
        val random = Random(1)
        val identity = FakeIdentityService(Identity("me", "Me", null))
        val load = LoadRandomPuzzle(
            LoadRandomDay(daily, FakeCompletionRepository(), LoadPastDay(daily, registry), registry, random),
            LoadRandomShared(ListCommunityPuzzles(community.shared, community.hidden, identity) { NOW }, random),
        )
        viewModel = RandomPuzzleViewModel(ALPHA, load, RandomPickMemory(), testDispatchers())
        val content: @Composable () -> Unit = {
            PuzzleBoxTheme {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                    RandomPuzzleSheet(ALPHA, pastGames, communityGames, PlayLayout.SinglePane, onOpen = { opened += it }, onDismiss = { dismissed++ }, viewModel = viewModel)
                }
            }
        }
        if (restoration != null) restoration.setContent(content) else rule.setContent(content)
        rule.waitForIdle()
    }

    fun node(tag: String) = rule.onNodeWithTag(tag)

    fun count(tag: String) = rule.onAllNodesWithTag(tag).fetchSemanticsNodes().size

    /** A tap, then a second of clock for the 150 ms return and the swap's fade. */
    fun tap(tag: String) {
        node(tag).performClick()
        rule.mainClock.advanceTimeBy(SETTLE_MS)
        rule.waitForIdle()
    }

    fun onNewToday() {
        mountSheet()
        tap("random.source.community")
    }

    companion object {
        const val SETTLE_MS = 1000L
    }
}
