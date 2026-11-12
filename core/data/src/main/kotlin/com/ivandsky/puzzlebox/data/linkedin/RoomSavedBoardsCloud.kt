package com.ivandsky.puzzlebox.data.linkedin

import androidx.room.withTransaction
import com.ivandsky.puzzlebox.data.cloud.CloudResult
import com.ivandsky.puzzlebox.data.cloud.CloudSpace
import com.ivandsky.puzzlebox.data.cloud.OwnerScope
import com.ivandsky.puzzlebox.data.cloud.SavedBoardsCloud
import com.ivandsky.puzzlebox.data.cloud.kinds.docIdOf
import com.ivandsky.puzzlebox.database.linkedin.LinkedInDatabase
import com.ivandsky.puzzlebox.database.linkedin.SavedBoardEntity

/**
 * Delete saved boards (A27): this device's saved boards and the signed-in account's cloud boards. The whole call runs
 * under [LinkedInStoreLock], taken once from outside: it reads the boards the screens show, deletes their cloud docs,
 * and only on [CloudResult.Ok] deletes the local rows (boards, their ghosts and the fetch-log marks that kept those
 * days from being read again). Any other result leaves the device exactly as it was, so a retry finds everything.
 *
 * Boards carry no tombstone: another device keeps its copy until it deletes it too, and a later read there uploads
 * that day again. Signed out, a board owned by an account cannot reach its cloud copy, so nothing is deleted
 * ([CloudResult.WrongAccount]); boards read while signed out never reached the cloud and go at once.
 */
internal class RoomSavedBoardsCloud(
    private val db: LinkedInDatabase,
    private val storeLock: LinkedInStoreLock,
    private val space: CloudSpace,
    private val ownerScope: OwnerScope,
) : SavedBoardsCloud {

    private val boards = db.savedBoardDao()

    override suspend fun deleteEverywhere(): CloudResult<Unit> = storeLock.withLock {
        val uid = ownerScope.writer()
        val shown = shownBoards(uid)
        when {
            uid == null && shown.any { it.ownerUid != null } -> CloudResult.WrongAccount
            uid == null || shown.isEmpty() -> deleteLocal(shown)
            else -> when (val cloud = space.delete(uid, KIND, shown.map(::docIdOf))) {
                is CloudResult.Ok -> deleteLocal(shown)
                else -> cloud
            }
        }
    }

    /** The boards the screens show now: the account's own and the ownerless ones. */
    private suspend fun shownBoards(uid: String?): List<SavedBoardEntity> {
        val visible = uid ?: ownerScope.visible.value
        return boards.all().filter { visible == null || it.ownerUid == null || it.ownerUid == visible }
    }

    private suspend fun deleteLocal(shown: List<SavedBoardEntity>): CloudResult<Unit> {
        db.withTransaction {
            for (board in shown) {
                db.ghostDao().deleteBoard(board.game, board.day)
                db.fetchLogDao().deleteBoardEntries(board.game, board.day)
                boards.delete(board.game, board.day)
            }
        }
        return CloudResult.Ok(Unit)
    }

    private companion object {
        const val KIND = "boards"
    }
}
