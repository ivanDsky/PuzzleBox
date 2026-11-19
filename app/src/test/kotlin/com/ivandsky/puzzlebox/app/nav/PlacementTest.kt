package com.ivandsky.puzzlebox.app.nav

import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.app.PlacementSamples
import com.ivandsky.puzzlebox.create.api.CreateKey
import com.ivandsky.puzzlebox.create.api.EditorKey
import com.ivandsky.puzzlebox.create.api.PublishedPuzzleKey
import com.ivandsky.puzzlebox.compete.api.DuelRevealKey
import com.ivandsky.puzzlebox.compete.api.NewDuelKey
import com.ivandsky.puzzlebox.insights.api.InsightsKey
import com.ivandsky.puzzlebox.insights.api.InsightsRecapKey
import com.ivandsky.puzzlebox.insights.api.InsightsStatsKey
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
    fun `the immersive set is exactly today's seven plus the replay, its compare, the Studio, New duel and a recap`() {
        val immersive = placementRows.filter { it.placement.chrome == Chrome.Immersive }.map { it.key }.toSet()

        assertEquals(
            setOf(
                GameKey::class, CustomGameKey::class, ResultKey::class, CustomResultKey::class,
                MatchLegKey::class, MatchLegResultKey::class, DuelRevealKey::class, ReplayKey::class, ReplayCompareKey::class,
                EditorKey::class, NewDuelKey::class, InsightsRecapKey::class,
            ),
            immersive,
        )
    }

    @Test
    fun `Insights' hub and stats page keep the bar and its recap story is immersive, all in the shell`() {
        assertEquals(
            mapOf(
                InsightsKey::class to Placement(Home.Shell, Chrome.Bar),
                InsightsStatsKey::class to Placement(Home.Shell, Chrome.Bar),
                InsightsRecapKey::class to Placement(Home.Shell, Chrome.Immersive),
            ),
            listOf(InsightsKey::class, InsightsStatsKey::class, InsightsRecapKey::class).associateWith { placements.getValue(it) },
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
        assertEquals(topLevelDestinations.map { it.tab }.toSet(), tabs.toSet())
        assertEquals(
            "a tab root's key differs from its destination's",
            topLevelDestinations.associate { it.tab to it.key::class },
            roots.toMap(),
        )
    }

    @Test
    fun `every registered key resolves to its row`() {
        val keys = PlacementSamples.everyRow()
        assertEquals("a registered key has no sample to resolve", registeredKeyClasses(), keys.map { it::class }.toSet())

        assertEquals(placements, keys.associate { it::class to placementOf(it) })
    }

    @Test
    fun `everything Custom's list opens is pushed on Community and keeps its chrome`() {
        assertEquals(
            mapOf(
                EditorKey::class to Placement(Home.PushedOn(Tab.Community), Chrome.Immersive),
                PublishedPuzzleKey::class to Placement(Home.PushedOn(Tab.Community), Chrome.Bar),
                FriendPuzzleKey::class to Placement(Home.PushedOn(Tab.Community), Chrome.Bar),
                CustomGameKey::class to Placement(Home.PushedOn(Tab.Community), Chrome.Immersive),
                CustomResultKey::class to Placement(Home.PushedOn(Tab.Community), Chrome.Immersive),
            ),
            listOf(EditorKey::class, PublishedPuzzleKey::class, FriendPuzzleKey::class, CustomGameKey::class, CustomResultKey::class)
                .associateWith { placements.getValue(it) },
        )
    }

    @Test
    fun `the legacy Create key resolves as retired`() {
        assertEquals(Placement(Home.Retired, Chrome.Bar), placementOf(CreateKey))
    }

    @Test
    fun `a key without a row fails with a message that names it`() {
        val failure = assertThrows(IllegalStateException::class.java) { placementOf(NoRow) }

        assertTrue(
            "expected the message to name NoRow, got: ${failure.message}",
            failure.message.orEmpty().contains("NoRow"),
        )
    }
}
