package com.ivandsky.puzzlebox.app

import android.app.Application
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.serialization.NavBackStackSerializer
import androidx.navigation3.runtime.serialization.NavKeySerializer
import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
import com.ivandsky.puzzlebox.app.nav.CatalogKey
import com.ivandsky.puzzlebox.app.nav.puzzleBoxNavKeys
import com.ivandsky.puzzlebox.history.api.HistoryKey
import com.ivandsky.puzzlebox.home.api.HomeKey
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.play.api.ReplayKey
import com.ivandsky.puzzlebox.play.api.ResultKey
import com.ivandsky.puzzlebox.profile.api.ProfileKey
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.encodeStructure
import kotlinx.serialization.serializer
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Process-death restore of the back stack: every key the app can push survives the
 * saved-state round trip through [puzzleBoxNavKeys], and a stack saved before the keys
 * moved into the feature api modules (written by nav3's reflective serializer under the
 * old `app.nav` class names) still restores into today's keys.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NavKeySavedStateTest {

    private val everyKey = listOf<NavKey>(
        HomeKey, PlayKey, ResultKey(61_200, 2), HistoryKey, ReplayKey("blob"), ProfileKey, CatalogKey,
    )

    private val restoreSerializer = NavBackStackSerializer(PolymorphicSerializer(NavKey::class))

    @Test
    fun `every key round-trips through the saved-state configuration`() {
        val saved = encodeToSavedState(restoreSerializer, NavBackStack(*everyKey.toTypedArray()), puzzleBoxNavKeys)

        val restored = decodeFromSavedState(restoreSerializer, saved, puzzleBoxNavKeys)

        assertEquals(everyKey, restored.toList())
    }

    @Test
    fun `a stack saved under the pre-split key class names restores`() {
        val saved = encodeToSavedState(NavBackStackSerializer(LegacyNavKeyWriter), NavBackStack(*everyKey.toTypedArray()))

        val restored = decodeFromSavedState(restoreSerializer, saved, puzzleBoxNavKeys)

        assertEquals(everyKey, restored.toList())
    }

    /**
     * Writes exactly what the pre-split build's `rememberNavBackStack(Home)` saved: nav3's
     * reflective NavKeySerializer layout (`type` = the key's class name, then `value`),
     * with the class names the keys had in `com.ivandsky.puzzlebox.app.nav`.
     */
    @OptIn(InternalSerializationApi::class)
    private object LegacyNavKeyWriter : NavKeySerializer<NavKey>() {
        private val legacyNames = mapOf(
            HomeKey::class to "com.ivandsky.puzzlebox.app.nav.Home",
            PlayKey::class to "com.ivandsky.puzzlebox.app.nav.Play",
            ResultKey::class to "com.ivandsky.puzzlebox.app.nav.Result",
            HistoryKey::class to "com.ivandsky.puzzlebox.app.nav.History",
            ReplayKey::class to "com.ivandsky.puzzlebox.app.nav.ReplayZip",
            ProfileKey::class to "com.ivandsky.puzzlebox.app.nav.Profile",
            CatalogKey::class to "com.ivandsky.puzzlebox.app.nav.Catalog",
        )

        @Suppress("UNCHECKED_CAST")
        override fun serialize(encoder: Encoder, value: NavKey) {
            encoder.encodeStructure(descriptor) {
                encodeStringElement(descriptor, 0, legacyNames.getValue(value::class))
                encodeSerializableElement(descriptor, 1, value::class.serializer() as KSerializer<NavKey>, value)
            }
        }
    }
}
