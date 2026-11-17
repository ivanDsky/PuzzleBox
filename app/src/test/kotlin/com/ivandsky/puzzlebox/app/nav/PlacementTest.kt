package com.ivandsky.puzzlebox.app.nav

import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.app.PlacementSamples
import com.ivandsky.puzzlebox.create.api.EditorKey
import com.ivandsky.puzzlebox.create.api.PublishedPuzzleKey
import com.ivandsky.puzzlebox.compete.api.DuelRevealKey
import com.ivandsky.puzzlebox.play.api.CommunityKey
import com.ivandsky.puzzlebox.play.api.CustomGameKey
import com.ivandsky.puzzlebox.play.api.CustomResultKey
import com.ivandsky.puzzlebox.play.api.FriendPuzzleKey
import com.ivandsky.puzzlebox.play.api.GameKey
import com.ivandsky.puzzlebox.play.api.MatchLegKey
import com.ivandsky.puzzlebox.play.api.MatchLegResultKey
import com.ivandsky.puzzlebox.play.api.ReplayCompareKey
import com.ivandsky.puzzlebox.play.api.ReplayKey
import com.ivandsky.puzzlebox.play.api.ResultKey
import kotlin.reflect.KClass
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.modules.SerializersModuleCollector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The placement table is the one answer to "where does this key live". These tests pin what
 * a table can get wrong without any key changing: a registered key with no row, a key class
 * written twice, the immersive set drifting, and the runtime safety net's message.
 */
class PlacementTest {

    /** A key outside the table. */
    private data object NoRow : NavKey

    @OptIn(ExperimentalSerializationApi::class)
    private fun registeredKeyClasses(): Set<KClass<*>> {
        val registered = mutableSetOf<KClass<*>>()
        puzzleBoxNavKeys.serializersModule.dumpTo(object : SerializersModuleCollector {
            override fun <T : Any> contextual(kClass: KClass<T>, serializer: KSerializer<T>) = Unit

            override fun <T : Any> contextual(
                kClass: KClass<T>,
                provider: (typeArgumentsSerializers: List<KSerializer<*>>) -> KSerializer<*>,
            ) = Unit

            override fun <Base : Any, Sub : Base> polymorphic(
                baseClass: KClass<Base>,
                actualClass: KClass<Sub>,
                actualSerializer: KSerializer<Sub>,
            ) {
                if (baseClass == NavKey::class) registered += actualClass
            }

            override fun <Base : Any> polymorphicDefaultSerializer(
                baseClass: KClass<Base>,
                defaultSerializerProvider: (value: Base) -> SerializationStrategy<Base>?,
            ) = Unit

            override fun <Base : Any> polymorphicDefaultDeserializer(
                baseClass: KClass<Base>,
                defaultDeserializerProvider: (className: String?) -> DeserializationStrategy<Base>?,
            ) = Unit
        })
        return registered
    }

    @Test
    fun `every registered key has a placement row`() {
        val registered = registeredKeyClasses()
        val missing = registered - placements.keys

        assertTrue("registered keys are not read from puzzleBoxNavKeys", registered.isNotEmpty())
        assertTrue("registered keys with no placement row: $missing", missing.isEmpty())
    }

    @Test
    fun `no key class has two rows`() {
        val repeated = placementRows.groupBy { it.key }.filterValues { it.size > 1 }.keys

        assertTrue("key classes with two rows: $repeated", repeated.isEmpty())
    }

    @Test
    fun `the immersive set is exactly today's seven plus the replay and its compare`() {
        val immersive = placementRows.filter { it.placement.chrome == Chrome.Immersive }.map { it.key }.toSet()

        assertEquals(
            setOf(
                GameKey::class, CustomGameKey::class, ResultKey::class, CustomResultKey::class,
                MatchLegKey::class, MatchLegResultKey::class, DuelRevealKey::class, ReplayKey::class, ReplayCompareKey::class,
            ),
            immersive,
        )
    }

    @Test
    fun `the tab set fits a bar of at most five tabs`() {
        assertTrue("tabs: ${topLevelDestinations.map { it.label }}", topLevelDestinations.size <= 5)
    }

    @Test
    fun `tab roots are exactly the tab set, one row per tab`() {
        val roots = placementRows.mapNotNull { row -> (row.placement.home as? Home.TabRoot)?.let { it.tab to row.key } }
        val tabs = roots.map { it.first }

        assertEquals("two tab roots share a tab: $tabs", tabs.distinct(), tabs)
        // Community is a tab only in the CustomInGames + CommunityPuzzles set, so it is not in topLevelDestinations.
        assertEquals(topLevelDestinations.map { it.tab }.toSet() + Tab.Community, tabs.toSet())
        assertEquals(
            "a tab root's key differs from its destination's",
            topLevelDestinations.associate { it.tab to it.key::class } + (Tab.Community to CommunityKey::class),
            roots.toMap(),
        )
    }

    @Test
    fun `every registered key resolves under the three flag states, and only the Custom keys move`() {
        val keys = PlacementSamples.everyRow()
        assertEquals("a registered key has no sample to resolve", registeredKeyClasses(), keys.map { it::class }.toSet())
        val base = keys.associate { it::class to placements.getValue(it::class) }

        assertEquals(base, keys.associate { it::class to placementOf(it, PlacementDelta.None) })
        assertEquals(
            base + mapOf(
                EditorKey::class to Placement(Home.PushedOn(Tab.Games), Chrome.Immersive),
                PublishedPuzzleKey::class to Placement(Home.PushedOn(Tab.Games), Chrome.Bar),
            ),
            keys.associate { it::class to placementOf(it, customInGamesPlacement) },
        )
        assertEquals(
            base + mapOf(
                EditorKey::class to Placement(Home.PushedOn(Tab.Community), Chrome.Immersive),
                PublishedPuzzleKey::class to Placement(Home.PushedOn(Tab.Community), Chrome.Bar),
                FriendPuzzleKey::class to Placement(Home.PushedOn(Tab.Community), Chrome.Bar),
                CustomGameKey::class to Placement(Home.PushedOn(Tab.Community), Chrome.Immersive),
                CustomResultKey::class to Placement(Home.PushedOn(Tab.Community), Chrome.Immersive),
            ),
            keys.associate { it::class to placementOf(it, communityPlacement) },
        )
    }

    @Test
    fun `a key without a row fails with a message that names it`() {
        val failure = assertThrows(IllegalStateException::class.java) { placementOf(NoRow, PlacementDelta.None) }

        assertTrue(
            "expected the message to name NoRow, got: ${failure.message}",
            failure.message.orEmpty().contains("NoRow"),
        )
    }
}
