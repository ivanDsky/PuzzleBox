package com.ivandsky.puzzlebox.game.replay

import com.ivandsky.puzzlebox.model.GameId
import org.junit.Assert.assertNotNull
import org.junit.Test
import kotlin.random.Random

class ReplayCodecFuzzTest {

    @Test
    fun `10 000 random buffers decode without an exception`() {
        val random = Random(SEED)
        repeat(BUFFERS) { run ->
            val noise = random.nextBytes(random.nextInt(MAX_NOISE_BYTES + 1))
            // Bare noise stops at the version byte and versioned noise at the inflater; only
            // deflated noise reaches the layout reader.
            val buffer = when (run % 3) {
                0 -> noise
                1 -> byteArrayOf(1) + noise
                else -> storedReplay(noise)
            }
            decodeOrFail(run, buffer)
        }
    }

    @Test
    fun `10 000 bit-flipped replays decode without an exception`() {
        val random = Random(SEED)
        repeat(BUFFERS) { run ->
            val stored = checkNotNull(ReplayCodec.encode(random.record()))
            // A flip in the stored bytes mostly breaks the deflate stream; a flip in the layout
            // gets through it to the layout reader.
            val buffer = if (run % 2 == 0) random.flipBits(stored) else storedReplay(random.flipBits(replayLayout(stored)))
            decodeOrFail(run, buffer)
        }
    }

    private fun decodeOrFail(run: Int, buffer: ByteArray) {
        val result = try {
            ReplayCodec.decode(buffer)
        } catch (e: Exception) {
            throw AssertionError("decode threw on buffer $run: ${hex(buffer)}", e)
        }
        // Encode refuses a breached cap or a decreasing stamp, so a record that encodes kept them all.
        val decoded = result.getOrNull() ?: return
        assertNotNull("buffer $run decoded to a record that does not encode: ${hex(buffer)}", ReplayCodec.encode(decoded))
    }

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

    private fun Random.record(): ReplayRecord {
        var atMs = 0L
        val moves = List(nextInt(40)) {
            atMs += nextLong(5_000)
            val args = IntArray(nextInt(10)) { if (nextBoolean()) nextInt(-9, 82) else nextInt() }
            StampedMove(atMs, ReplayMove(nextInt(ReplayCodec.MAX_KIND + 1), args))
        }
        return ReplayRecord(GameId("zip"), ReplayOutcome.entries.random(this), atMs + nextLong(5_000), moves)
    }

    private fun Random.flipBits(bytes: ByteArray): ByteArray {
        val flipped = bytes.copyOf()
        repeat(nextInt(1, 4)) {
            val bit = nextInt(flipped.size * 8)
            flipped[bit / 8] = (flipped[bit / 8].toInt() xor (1 shl (bit % 8))).toByte()
        }
        return flipped
    }

    private companion object {
        const val SEED = 21
        const val BUFFERS = 10_000
        const val MAX_NOISE_BYTES = 512
    }
}
