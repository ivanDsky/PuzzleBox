package com.ivandsky.puzzlebox.create.editor

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.semantics.SemanticsProperties
import com.ivandsky.puzzlebox.create.FakeGameEditor
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.ValidatePublish
import com.ivandsky.puzzlebox.game.editor.PublishBlock
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The readiness line (spec A8, A10, A25): one phrase per domain block, one "Fix the board"
 * for every game-owned block, Ready when nothing blocks.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReadinessCopyTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val nonEmptyText = SemanticsMatcher("has non-empty text") { node ->
        node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.any { it.text.isNotBlank() }
    }

    private fun show(blocks: List<PublishBlock>) {
        compose.setContent {
            PuzzleBoxTheme {
                ActionArea(passed = null, canTestSolve = false, onTestSolve = {}, blocks = blocks)
            }
        }
    }

    @Test
    fun everyDomainBlockIdHasItsOwnPhrase() {
        show(ValidatePublish.blockIds.map(::PublishBlock))
        ValidatePublish.blockIds.forEach { id -> compose.onNodeWithTag("create.readiness.$id").assert(nonEmptyText) }
        compose.onAllNodesWithTag("create.readiness.ready").assertCountEquals(0)
    }

    @Test
    fun gameOwnedBlocksReadAsOneBoardPhrase() {
        show(listOf(PublishBlock("fake.unknown"), PublishBlock(PublishBlock.SPLIT_REGION), PublishBlock(PublishBlock.NEEDS_GUESSING)))
        compose.onAllNodesWithTag("create.readiness.board").assertCountEquals(1)
        compose.onNodeWithTag("create.readiness.board").assert(nonEmptyText)
        compose.onAllNodesWithTag("create.readiness.fake.unknown").assertCountEquals(0)
    }

    @Test
    fun noBlocksReadsReady() {
        show(emptyList())
        compose.onNodeWithTag("create.readiness.ready").assert(nonEmptyText)
    }

    @Test
    fun aFakeEditorsBlankDraftListsItsDomainBlocks() {
        val draft = FakeGameEditor().draft(0)
        val blocks = ValidatePublish()(draft, FakeGameEditor.defaultVerdict(draft.state.draft), title = "", testSolve = null)
        show(blocks)
        blocks.forEach { compose.onNodeWithTag("create.readiness.${it.id}").assert(nonEmptyText) }
    }
}
