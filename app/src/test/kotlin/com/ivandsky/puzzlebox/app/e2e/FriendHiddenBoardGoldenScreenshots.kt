package com.ivandsky.puzzlebox.app.e2e

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.play.api.PlayKey
import java.io.File
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The friend page's hidden board (nav-rethink M12-8) in each of the five real games, light and dark, on a phone, with
 * the puzzle's real board blurred under "Hidden until you play". Recorded with
 * `-Proborazzi.record=true` into `snapshots/friend-hidden-board/` (untracked review captures).
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// DrainTestApplication (plain Application + looper drain): the merged manifest's PuzzleBoxApplication would start its own Koin.
@Config(sdk = [34], application = DrainTestApplication::class, qualifiers = "w411dp-h891dp-420dpi")
class FriendHiddenBoardGoldenScreenshots(name: String) {

    private val game: FriendGame = FRIEND_GAMES.single { it.toString() == name }
    private val world = FriendWorld(game.flags)

    @After
    fun close() = world.close()

    @Test fun `hidden board light`() = capture(dark = false)
    @Test fun `hidden board dark`() = capture(dark = true)

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(dark: Boolean) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        shellRobot(graph = world.graph, darkTheme = dark) {
            val code = world.publishFriendPuzzle(game, "From a friend")
            world.signInFriend()
            tapTab(PlayKey)
            openByCode(code)
            awaitTag("friend.card.board.hidden")
            rule.waitForIdle()
            captureScreenRoboImage(out(dark))
        }
    }

    private fun out(dark: Boolean): String =
        File(checkNotNull(System.getProperty("user.dir")))
            .resolve("snapshots/friend-hidden-board/$game-${if (dark) "dark" else "light"}.png")
            .canonicalFile
            .also { checkNotNull(it.parentFile).mkdirs() }
            .absolutePath

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun games(): List<Array<Any>> = FRIEND_GAMES.map { arrayOf<Any>(it.toString()) }
    }
}
