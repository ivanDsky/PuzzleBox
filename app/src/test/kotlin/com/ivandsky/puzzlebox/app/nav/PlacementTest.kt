package com.ivandsky.puzzlebox.app.nav

import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.compete.api.DuelRevealKey
import com.ivandsky.puzzlebox.play.api.CustomGameKey
import com.ivandsky.puzzlebox.play.api.CustomResultKey
import com.ivandsky.puzzlebox.play.api.GameKey
import com.ivandsky.puzzlebox.play.api.MatchLegKey
import com.ivandsky.puzzlebox.play.api.MatchLegResultKey
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
    fun `the immersive set is exactly today's seven`() {
        val immersive = placementRows.filter { it.placement.chrome == Chrome.Immersive }.map { it.key }.toSet()

        assertEquals(
            setOf(
                GameKey::class, CustomGameKey::class, ResultKey::class, CustomResultKey::class,
                MatchLegKey::class, MatchLegResultKey::class, DuelRevealKey::class,
            ),
            immersive,
        )
    }

    @Test
    fun `tab roots are at most five with distinct tabs`() {
        val tabs = placementRows.map { it.placement.home }.filterIsInstance<Home.TabRoot>().map { it.tab }

        assertTrue("more than five tab roots: $tabs", tabs.size <= 5)
        assertEquals("two tab roots share a tab: $tabs", tabs.distinct(), tabs)
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
