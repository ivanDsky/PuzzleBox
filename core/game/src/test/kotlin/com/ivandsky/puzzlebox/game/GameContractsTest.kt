package com.ivandsky.puzzlebox.game

import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/**
 * Contract-level behavior, exercised through a complete fake game (F1 §5): the plugin
 * composes, the codec seam round-trips, records normalize, and the puzzle seam serves
 * and fails with typed errors. If a game cannot be built against these contracts
 * unchanged, that is an F1 design bug by definition.
 */
class GameContractsTest {

    @Test
    fun `plugin composes all five concerns and serves the play loop end to end`() {
        val plugin = TestGame.plugin()

        assertEquals(TestGame.id, plugin.definition.id)
        assertEquals("Test Game", plugin.definition.displayName)
        assertEquals(Cadence.Daily(ZoneId.of("UTC")), plugin.definition.cadence)

        // Move validation flows through the game's own verdict type.
        val fresh = TestGame.State(solved = false)
        assertSame(TestGame.Valid, plugin.rules.validate(TestGame.PlayMove(0), fresh))
        assertSame(TestGame.Valid, plugin.rules.validate(TestGame.PlayMove(99), fresh))
        assertSame(TestGame.Invalid, plugin.rules.validate(TestGame.PlayMove(0), TestGame.State(solved = true)))

        // Renderer-model factory is pure: same input, same model, state untouched.
        val hint = plugin.rules.hint(fresh)
        assertEquals(TestGame.Model(solved = false), plugin.rendererModel(fresh, hint, null))

        // The verdict reaches the renderer model unchanged — null, valid, or invalid.
        assertSame(TestGame.Valid, plugin.rendererModel(fresh, hint, TestGame.Valid).lastVerdict)
        assertSame(TestGame.Invalid, plugin.rendererModel(fresh, null, TestGame.Invalid).lastVerdict)

        // Completion flows session -> record.
        val done = TestGame.Session(moveCount = 2, done = true)
        assertTrue(plugin.rules.isComplete(TestGame.State(solved = true)))
        val record = plugin.score.completion(done)
        requireNotNull(record)
        assertEquals(1_000L, record.time)
        assertEquals(2, record.hints)
        assertNull(record.firstCompletedAt) // stamped by core/data, never by the game
        assertEquals(false, record.isRecord)

        // Unfinished sessions emit nothing.
        assertNull(plugin.score.completion(TestGame.Session(moveCount = 1, done = false)))
    }

    @Test
    fun `initialSession and stateOf round-trip a puzzle through the play loop`() {
        val plugin = TestGame.plugin()

        // A fetched puzzle yields a fresh session; stateOf derives the rules state from it.
        val freshSession = plugin.initialSession(TestGame.Definition.blank()).getOrNull()
        requireNotNull(freshSession)
        assertEquals(TestGame.Session(), freshSession)
        assertEquals(TestGame.State(solved = false), plugin.stateOf(freshSession))

        // A restored session derives the same state it was saved from.
        val done = TestGame.Session(moveCount = 3, done = true)
        val restored = TestGame.SessionCodec
            .deserialize(TestGame.SessionCodec.serialize(done))
            .getOrNull()
        requireNotNull(restored)
        assertEquals(TestGame.State(solved = true), plugin.stateOf(restored))

        // A foreign envelope is a typed rejection, never a throw.
        val foreign = Puzzle(GameId("other"), VariantId("other/v1"), 1, ByteArray(0))
        val rejected = plugin.initialSession(foreign)
        assertTrue(rejected is DecodeResult.Err)
        assertTrue((rejected as DecodeResult.Err).error is DecodeError.Malformed)
        assertNull(rejected.getOrNull())
    }

    @Test
    fun `registry looks plugins up by id in registration order and refOf rebuilds refs`() {
        val plugin = TestGame.plugin()
        val registry = GameRegistry(listOf(plugin))

        assertEquals(listOf(plugin), registry.all)
        assertSame(plugin, registry[TestGame.id])
        assertNull(registry[GameId("unregistered")])

        val daily = plugin.refOf(PuzzleKey.Daily(TestGame.date, 7))
        assertEquals(TestGame.id, daily.gameId)
        assertEquals(PuzzleKey.Daily(TestGame.date, 7), daily.key)

        val custom = plugin.refOf(PuzzleKey.Custom("K7Q2MX"))
        assertEquals(TestGame.id, custom.gameId)
        assertEquals(PuzzleKey.Custom("K7Q2MX"), custom.key)
    }

    @Test
    fun `registry refuses a plugin that claims the reserved custom game id`() {
        val reserved = GameId(PuzzleKey.Custom.NAMESPACE)
        val base = TestGame.plugin()
        val custom = GamePlugin(
            object : GameDefinition by TestGame.Definition {
                override val id = reserved
            },
            base.rules,
            base.session,
            base.score,
            base.stateOf,
            base.initialSession,
            base.refOf,
            base.replaySession,
            base.rendererModel,
        )

        val listed = assertThrows(IllegalArgumentException::class.java) {
            GameRegistry(listOf(base, custom))
        }
        assertEquals("GameId 'custom' is reserved", listed.message)
        assertThrows(IllegalArgumentException::class.java) {
            GameRegistry(listOf(base, custom), unlisted = setOf(reserved))
        }
    }

    @Test
    fun `definition decode-encode is symmetric over envelopes`() {
        val definition = TestGame.Definition
        val bytes = byteArrayOf(7, 7, 7)

        val decoded = definition.decode(bytes).getOrNull()
        requireNotNull(decoded)
        assertEquals(TestGame.id, decoded.gameId)
        assertEquals(TestGame.variant, decoded.variant)
        assertEquals(1, decoded.formatVersion)

        // Symmetric: bytes survive an encode(decode(x)) trip untouched.
        assertTrue(bytes.contentEquals(definition.encode(decoded)))
    }

    @Test
    fun `session codec round-trips and rejects malformed blobs with a typed error`() {
        val codec = TestGame.SessionCodec
        val session = TestGame.Session(moveCount = 3, done = true)

        assertEquals(session, codec.deserialize(codec.serialize(session)).getOrNull())

        val err = codec.deserialize(byteArrayOf(9, 9))
        assertTrue(err is DecodeResult.Err)
        assertTrue((err as DecodeResult.Err).error is DecodeError.Malformed)
    }

    @Test
    fun `fetch results expose the value only on success`() {
        val ok: FetchResult<Puzzle> = FetchResult.Ok(TestGame.Definition.blank())
        assertEquals(TestGame.Definition.blank(), ok.getOrNull())

        val miss: FetchResult<Puzzle> = FetchResult.Err(FetchError.Unavailable("no fixture"))
        assertNull(miss.getOrNull())
    }

    @Test
    fun `record stats slot is versioned and value-equal`() {
        val stats = RecordStats(1, "{\"v\":1}")
        assertEquals(stats, RecordStats(1, "{\"v\":1}"))
        assertTrue(stats != RecordStats(2, "{\"v\":1}"))
        assertTrue(stats != RecordStats(1, "{}"))
    }
}
