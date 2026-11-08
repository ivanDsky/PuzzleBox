package com.ivandsky.puzzlebox.app.e2e

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.create.api.CreateKey
import com.ivandsky.puzzlebox.data.repository.DraftRepository
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import com.ivandsky.puzzlebox.testing.repository.FakeDraftRepository
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * M5-01-d's walking skeleton over the real shell with `TextImport` forced on: on the Create
 * root the header Import pill opens the sheet, a pasted Queens board and one tap on Import open
 * the new draft in the Studio with the verdict the same board would show painted by hand
 * (Unique), and back on the root the imported draft's row exists.
 */
@RunWith(RobolectricTestRunner::class)
// Plain Application: the merged manifest's PuzzleBoxApplication would start its own Koin.
// A real phone's height: the Studio's board slot needs room for its grid.
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w411dp-h891dp")
class ImportTextE2ETest {

    private val drafts = FakeDraftRepository()

    @Test
    fun `pasting a Queens board and tapping Import opens an imported draft in the Studio`() =
        shellRobot(
            graph = E2eKoinGraph(
                E2E_TODAY,
                overrides = module { single<DraftRepository> { drafts } },
                releaseFlags = FakeReleaseFlags(ReleaseFlag.TextImport),
            ),
        ) {
            tapTab(CreateKey)
            assertRoute(CREATE)
            tap(PILL)
            awaitTag(SHEET)
            rule.onNodeWithTag(SUBMIT).assertIsNotEnabled()
            rule.onNodeWithTag(TEXT).performTextInput(UNIQUE)
            tap(SUBMIT)

            awaitIdling { rule.onAllNodesWithTag(BOARD, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
            assertRoute(EDITOR)
            awaitIdling {
                val unique = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Unique")
                rule.onAllNodes(hasTestTag(VERDICT) and unique, useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty()
            }
            val stored = drafts.rows.values.single()
            assertTrue("the draft is flagged imported", stored.imported)

            back()
            assertRoute(CREATE)
            awaitTag(ROW)
            rule.onAllNodesWithTag(ROW).assertCountEquals(1)
        }

    private companion object {
        const val CREATE = "route.create"
        const val EDITOR = "route.editor"
        const val PILL = "create.header.import"
        const val SHEET = "create.import.sheet"
        const val TEXT = "create.import.text"
        const val SUBMIT = "create.import.submit"
        const val BOARD = "create.editor.board"
        const val VERDICT = "create.editor.pill"
        const val ROW = "create.draft.row"
        // games/queens' queens-unique-5.txt fixture
        const val UNIQUE = "puzzlebox queens v1\n00001\n00031\n42033\n43333\n43333\n"
    }
}
