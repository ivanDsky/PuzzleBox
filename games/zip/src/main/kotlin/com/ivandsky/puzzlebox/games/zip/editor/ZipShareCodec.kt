package com.ivandsky.puzzlebox.games.zip.editor

import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.EditorShareCodec
import com.ivandsky.puzzlebox.games.zip.Wall
import com.ivandsky.puzzlebox.games.zip.WallDirection
import com.ivandsky.puzzlebox.games.zip.codec.ZipSessionCursor
import com.ivandsky.puzzlebox.games.zip.engine.ZipVariants

/**
 * Versioned board-only Zip share bytes (ADR-0005, ADR-0020), cells row-major (n ≤ 8, one byte):
 *
 * ```
 * v1: [u8 version][u8 n]
 *     [u8 w][w × (u8 cell, u8 dir 0=RIGHT|1=DOWN)]   (walls ascending by cell, dir)
 *     [u8 k][k × u8 cell]                            (waypoints, label order, no repeats)
 * ```
 *
 * The path never travels: the recipient decodes a path-less draft and re-solves it from the
 * waypoints.
 */
internal object ZipShareCodec : EditorShareCodec<ZipDraft> {
    override val shareFormatVersion: Int = 1

    override fun encodeShare(draft: ZipDraft): ByteArray {
        val walls = draft.walls.sortedWith(compareBy({ it.cell.idx }, { it.direction }))
        val out = ByteArray(4 + 2 * walls.size + draft.waypoints.size)
        var pos = 0
        out[pos++] = shareFormatVersion.toByte()
        out[pos++] = draft.n.toByte()
        out[pos++] = walls.size.toByte()
        for (wall in walls) {
            out[pos++] = wall.cell.idx.toByte()
            out[pos++] = wall.direction.ordinal.toByte()
        }
        out[pos++] = draft.waypoints.size.toByte()
        for (cell in draft.waypoints) out[pos++] = cell.toByte()
        return out
    }

    /**
     * version > 1 ⇒ UnsupportedVersion(found, 1); unknown size ⇒ UnknownVariant;
     * anything else invalid (incl. repeated waypoints) ⇒ Malformed.
     */
    override fun decodeShare(bytes: ByteArray): DecodeResult<ZipDraft> {
        val cursor = ZipSessionCursor(bytes)
        val version = cursor.u8() ?: return malformed("empty share bytes")
        if (version > shareFormatVersion) {
            return DecodeResult.Err(DecodeError.UnsupportedVersion(found = version, maxKnown = shareFormatVersion))
        }
        if (version != shareFormatVersion) return malformed("share version $version was never written")
        val n = cursor.u8() ?: return malformed("truncated board size")
        val variant = "zip/n$n"
        if (ZipVariants.byId(VariantId(variant)) == null) return DecodeResult.Err(DecodeError.UnknownVariant(variant))
        val walls = readWalls(cursor, n) ?: return malformed("malformed walls")
        val waypoints = cursor.cells(n * n) ?: return malformed("malformed waypoints")
        if (waypoints.toSet().size != waypoints.size) return malformed("repeated waypoint cell")
        if (cursor.pos != bytes.size) return malformed("${bytes.size - cursor.pos} trailing bytes")
        return DecodeResult.Ok(ZipDraft(n, walls, emptyList(), waypoints.map { it.idx }))
    }

    /** Walls ascending and on interior edges, as [encodeShare] lays them out; null = malformed. */
    private fun readWalls(cursor: ZipSessionCursor, n: Int): Set<Wall>? {
        val walls = LinkedHashSet<Wall>()
        var last = -1
        repeat(cursor.u8() ?: return null) {
            val cell = cursor.cell(n * n) ?: return null
            val direction = WallDirection.entries.getOrNull(cursor.u8() ?: return null) ?: return null
            val interior = when (direction) {
                WallDirection.RIGHT -> cell.idx % n < n - 1
                WallDirection.DOWN -> cell.idx / n < n - 1
            }
            val key = cell.idx * 2 + direction.ordinal
            if (!interior || key <= last) return null
            last = key
            walls.add(Wall(cell, direction))
        }
        return walls
    }

    private fun malformed(detail: String): DecodeResult.Err = DecodeResult.Err(DecodeError.Malformed(detail))
}
