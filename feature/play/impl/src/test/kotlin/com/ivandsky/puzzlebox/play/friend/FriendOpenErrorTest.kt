package com.ivandsky.puzzlebox.play.friend

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.model.PostState
import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
import com.ivandsky.puzzlebox.data.model.SharedPuzzleDoc
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.LoadSharedPuzzle
import com.ivandsky.puzzlebox.domain.OpenOutcome
import com.ivandsky.puzzlebox.domain.SyncReceivedStatus
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.gameui.editor.GameEditorRegistry
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.FakeGames
import com.ivandsky.puzzlebox.play.api.FriendPuzzleKey
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchRepository
import com.ivandsky.puzzlebox.testing.repository.FakeReceivedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Every way a code can fail to open, over the real [LoadSharedPuzzle] and fake backend and
 * store: the friend route's full-screen state per outcome with Retry and Update, and the Enter
 * code sheet's inline field error and notices. The registered game has no editor, so a
 * published board of it fails its check.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class FriendOpenErrorTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val game = TestGames.alpha
    private val fakes = FakeGames(listOf(game))
    private val code = requireNotNull(ShareCode.parse("K7Q2MX"))
    private val shared = FakeSharedPuzzleRepository().apply { signedInUid = AUTHOR }
    private val received = FakeReceivedPuzzleRepository()
    private val opened = mutableListOf<String>()
    private val load = LoadSharedPuzzle(
        registry = fakes.games,
        shared = shared,
        received = received,
        budget = { Budget.within(1_000L, System::nanoTime) },
        clock = { Instant.EPOCH },
    )
    private val now = object : DispatcherProvider {
        override val main = Dispatchers.Unconfined
        override val io = Dispatchers.Unconfined
        override val default = Dispatchers.Unconfined
    }
    private val uris = object : UriHandler {
        override fun openUri(uri: String) {
            opened += uri
        }
    }

    @Test
    fun `a code with no puzzle shows not found`() = showsOnRoute("friend.error.notFound")

    @Test
    fun `a withdrawn puzzle shows no longer available`() = showsOnRoute("friend.error.noLongerAvailable") {
        publish()
        runBlocking { shared.unpublish(code) }
    }

    @Test
    fun `a newer format shows needs update`() = showsOnRoute("friend.error.needsUpdate") {
        publish(formatVersion = SharedPuzzleDoc.FORMAT_VERSION + 1)
    }

    @Test
    fun `a game this build lacks shows unknown game`() = showsOnRoute("friend.error.unknownGame") {
        publish(gameId = "sudoku9")
    }

    @Test
    fun `a board that fails its check shows failed check`() = showsOnRoute("friend.error.failedCheck") {
        publish()
    }

    @Test
    fun `a new code with no connection shows offline`() = showsOnRoute("friend.error.offline") {
        shared.online = false
    }

    @Test
    fun `a stored puzzle opens its card with no connection`() {
        runBlocking { received.upsert(stored()) }
        shared.online = false
        showRoute()

        node("friend.card.title").assertIsDisplayed()
        assertEquals(0, count("friend.error.offline"))
    }

    private fun showsOnRoute(tag: String, arrange: () -> Unit = {}) {
        arrange()
        showRoute()

        node(tag).assertIsDisplayed()
        composeRule.onNode(hasTestTag("friend.error.icon") and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true)
            .assertIsDisplayed()
        assertEquals(0, count("friend.card.loading"))
    }

    @Test
    fun `a key whose code does not parse is not found`() {
        showRoute(FriendPuzzleKey("K7Q2M"))

        node("friend.error.notFound").assertIsDisplayed()
    }

    @Test
    fun `every failure reads differently and only some offer an action`() {
        val failures = listOf(
            OpenOutcome.NotFound,
            OpenOutcome.NoLongerAvailable,
            OpenOutcome.NeedsUpdate(found = 2),
            OpenOutcome.UnknownGame("sudoku9"),
            OpenOutcome.FailedCheck,
            OpenOutcome.Offline,
        )
        val copies = failures.map { requireNotNull(it.errorCopy(code)) }

        assertEquals(failures.size, copies.map { it.tag }.toSet().size)
        assertEquals(failures.size, copies.map { it.title }.toSet().size)
        assertEquals(failures.size, copies.map { it.body }.toSet().size)
        assertEquals(failures.size, copies.map { it.glyph }.toSet().size)
        assertEquals(OpenErrorGlyph.Offline, OpenOutcome.Offline.errorCopy(code)?.glyph)
        assertEquals(OpenErrorAction.Update, OpenOutcome.NeedsUpdate(found = 2).errorCopy(code)?.action)
        assertEquals(OpenErrorAction.Retry, OpenOutcome.Offline.errorCopy(code)?.action)
        assertTrue(copies.count { it.action != null } == 2)
        assertNull(OpenOutcome.Opened(stored()).errorCopy(code))
    }

    @Test
    fun `Retry on the offline state opens the code again`() {
        shared.online = false
        publish()
        showRoute()
        node("friend.error.offline").assertIsDisplayed()

        shared.online = true
        action("friend.error.offline").performClick()
        composeRule.waitForIdle()

        assertEquals(0, count("friend.error.offline"))
        node("friend.error.failedCheck").assertIsDisplayed()
    }

    @Test
    fun `Update on the needs-update state opens the store listing`() {
        publish(formatVersion = SharedPuzzleDoc.FORMAT_VERSION + 1)
        showRoute()

        action("friend.error.needsUpdate").performClick()

        assertEquals(listOf(STORE_LISTING), opened)
    }

    @Test
    fun `the failures without an action offer none`() {
        publish(gameId = "sudoku9")
        showRoute()

        node("friend.error.unknownGame").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodes(actionIn("friend.error.unknownGame")).fetchSemanticsNodes().size)
    }

    @Test
    fun `an unknown code shows the field error in the sheet and stays there`() {
        showSheet()

        type("K7Q2MX")
        open()

        node("friend.code.error").assertIsDisplayed()
        node("friend.code.sheet").assertIsDisplayed()
        assertTrue(sheetOpened.isEmpty())
    }

    @Test
    fun `editing the code clears its field error`() {
        showSheet()
        type("K7Q2MX")
        open()

        node("friend.code.field").performTextClearance()
        type("K7Q2M")
        composeRule.waitForIdle()

        assertEquals(0, count("friend.code.error"))
    }

    @Test
    fun `an offline check shows a notice whose Try again checks again`() {
        shared.online = false
        showSheet()
        type("K7Q2MX")
        open()
        node("friend.error.offline").assertIsDisplayed()
        assertEquals(0, count("friend.code.error"))

        shared.online = true
        node("friend.code.notice.action").performClick()
        composeRule.waitForIdle()

        assertEquals(0, count("friend.error.offline"))
        node("friend.code.error").assertIsDisplayed()
    }

    @Test
    fun `a withdrawn or newer puzzle shows its notice in the sheet`() {
        publish(formatVersion = SharedPuzzleDoc.FORMAT_VERSION + 1)
        showSheet()
        type("K7Q2MX")
        open()

        node("friend.error.needsUpdate").assertIsDisplayed()
        node("friend.code.notice.action").performClick()
        assertEquals(listOf(STORE_LISTING), opened)
    }

    @Test
    fun `a code that opens hands itself on once the sheet hides`() {
        runBlocking { received.upsert(stored()) }
        showSheet()

        type("k7q-2mx")
        open()

        composeRule.waitUntil(TIMEOUT_MS) { sheetOpened.isNotEmpty() }
        assertEquals(listOf(code), sheetOpened)
        assertEquals(0, count("friend.code.error"))
    }

    @Test
    fun `Open waits for six valid characters`() {
        showSheet()

        type("K7Q2M")
        node("friend.code.open").assertIsNotEnabled()
        type("U")
        node("friend.code.open").assertIsNotEnabled()
        node("friend.code.field").performTextClearance()
        type("K7Q-2MX")
        node("friend.code.open").assertIsEnabled()
    }

    @Test
    fun `a bad link prefills only its last segment, capped`() {
        val long = ShareCode.LINK_PREFIX + "K".repeat(500)

        assertEquals("K7Q2M", codePrefill(ShareCode.LINK_PREFIX + "K7Q2M"))
        assertEquals("K7Q2M", codePrefill("  K7Q2M \n"))
        assertTrue(codePrefill(long).length <= PREFILL_CAP)
        assertTrue(codePrefill(long).all { it == 'K' })
        assertEquals("", codePrefill(""))
        val emoji = "A".repeat(PREFILL_CAP - 1) + "\uD83D\uDE00"
        assertTrue(!codePrefill(emoji).last().isHighSurrogate())
    }

    @Test
    fun `the sheet prefills a bad link's capped text`() {
        showSheet(initialText = ShareCode.LINK_PREFIX + "K7Q2M" + "X".repeat(200))

        val text = node("friend.code.field").fetchSemanticsNode().config[SemanticsProperties.EditableText].text

        assertTrue(text.length <= PREFILL_CAP)
        assertTrue(text.startsWith("K7Q2M"))
    }

    private val sheetOpened = mutableListOf<ShareCode>()

    private fun showSheet(initialText: String = "") {
        val viewModel = EnterCodeViewModel(load, FakeMatchRepository(), now)
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                CompositionLocalProvider(LocalUriHandler provides uris) {
                    EnterCodeSheet(onOpen = { sheetOpened += it }, onDismiss = {}, initialText = initialText, viewModel = viewModel)
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun showRoute(key: FriendPuzzleKey = FriendPuzzleKey(code.value)) {
        val viewModel = routeViewModel(key)
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                CompositionLocalProvider(LocalUriHandler provides uris) {
                    FriendPuzzleRoute(key = key, onPlay = {}, onBack = {}, viewModel = viewModel)
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun routeViewModel(key: FriendPuzzleKey = FriendPuzzleKey(code.value)) = FriendPuzzleViewModel(
        key = key,
        load = load,
        sync = SyncReceivedStatus(shared, received),
        received = received,
        completions = FakeCompletionRepository(),
        saved = FakeSavedPlayRepository(),
        games = fakes.games,
        plays = fakes.registry,
        editors = GameEditorRegistry(emptyList()),
        dispatchers = now,
        board = signedOutBoard(),
    )

    private fun publish(gameId: String = game.value, formatVersion: Int = SharedPuzzleDoc.FORMAT_VERSION) {
        val online = shared.online
        shared.online = true
        runBlocking {
            shared.publish(
                SharedPuzzleDoc(
                    code = code, formatVersion = formatVersion, gameId = gameId, variant = "alpha/n8",
                    shareCodecVersion = 1, payload = byteArrayOf(1, 2, 3), title = "Crown the castle",
                    authorName = "Jonas", note = "", authorUid = AUTHOR, authorTimeMs = 102_000L, authorHints = 0,
                    createdAtMs = 0L, unpublished = false,
                ),
            )
        }
        shared.online = online
    }

    private fun stored() = ReceivedPuzzle(
        code = code, gameId = game, variant = VariantId("alpha/n8"), sharedFormatVersion = 1,
        puzzleBytes = byteArrayOf(1, 2, 3), title = "Crown the castle", authorName = "Jonas", note = "",
        authorTimeMs = 102_000L, authorHints = 0, authorUid = AUTHOR, openedAt = Instant.EPOCH, unpublished = false,
        postState = PostState.Pending, postAttempts = 0, difficulty = null,
    )

    private fun type(text: String) {
        node("friend.code.field").performTextInput(text)
        composeRule.waitForIdle()
    }

    private fun open() {
        node("friend.code.open").performClick()
        composeRule.waitForIdle()
    }

    private fun node(tag: String): SemanticsNodeInteraction = composeRule.onNodeWithTag(tag, useUnmergedTree = true)

    private fun actionIn(tag: String): SemanticsMatcher = hasClickAction() and hasAnyAncestor(hasTestTag(tag))

    private fun action(tag: String): SemanticsNodeInteraction = composeRule.onNode(actionIn(tag))

    private fun count(tag: String) =
        composeRule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().size

    private companion object {
        const val AUTHOR = "uid-jonas"
        const val PREFILL_CAP = 32
        const val TIMEOUT_MS = 5_000L
        const val STORE_LISTING = "https://play.google.com/store/apps/details?id=com.ivandsky.puzzlebox"
    }
}
