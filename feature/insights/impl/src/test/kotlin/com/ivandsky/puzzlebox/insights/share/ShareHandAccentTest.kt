package com.ivandsky.puzzlebox.insights.share

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.insights.recap.LocalGameBrands
import com.ivandsky.puzzlebox.insights.recap.SolvedFixtures
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A fixed style is the same image whoever shares it: Color and Night, games' accents included, draw the same pixels
 * whether the viewer's theme is light or dark. Classic follows the theme, so its two images differ.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1080dp-h1920dp-mdpi")
class ShareHandAccentTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val viewerDark = mutableStateOf(false)
    private val style = mutableStateOf(ShareStyle.Classic)

    @Test
    fun `Color and Night draw the same accents in a light and a dark theme, Classic does not`() {
        val content = RecapShareContent.of("October 2026", "Ivan", ShareContract.default, SolvedFixtures.gameName)
        rule.setContent {
            PuzzleBoxTheme(darkTheme = viewerDark.value) {
                CompositionLocalProvider(LocalGameBrands provides SolvedFixtures.brands) { RecapShareImage(content, style.value) }
            }
        }

        assertTrue("Color follows the theme", same(ShareStyle.Color))
        assertTrue("Night follows the theme", same(ShareStyle.Night))
        assertFalse("Classic ignores the theme", same(ShareStyle.Classic))
    }

    private fun same(shown: ShareStyle): Boolean {
        style.value = shown
        val (light, dark) = listOf(false, true).map { viewerDark.value = it; pixels() }
        return light.contentEquals(dark)
    }

    private fun pixels(): IntArray {
        rule.waitForIdle()
        val image = rule.onNodeWithTag("recap.share.image").captureToImage()
        return IntArray(image.width * image.height).also { image.readPixels(it, 0, 0, image.width, image.height) }
    }
}
