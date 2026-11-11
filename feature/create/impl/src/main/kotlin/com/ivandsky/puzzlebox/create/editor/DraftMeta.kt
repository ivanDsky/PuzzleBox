package com.ivandsky.puzzlebox.create.editor

import com.ivandsky.puzzlebox.data.model.Draft
import com.ivandsky.puzzlebox.domain.ComputeCodename
import com.ivandsky.puzzlebox.domain.CreateDraftCopy
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The open draft's identity in the Studio's top bar (02 handoff "Top bar", "Overflow menu"):
 * its [title], a fresh [rollCodename] for the rename dice, [rename] within 1–[Draft.TITLE_MAX] characters (spec A25), [duplicate] through
 * [CreateDraftCopy], and [delete]. A rename is handed to [store], the Studio's one writer, so
 * it is never reordered against board edits; once [locked] (the draft is published) the title
 * is fixed and a rename is refused. Delete stores nothing itself: the Create root
 * removes the draft and offers its 5 s Undo (22).
 */
class DraftMeta(
    private val draftId: String,
    private val copyDraft: CreateDraftCopy,
    private val scope: CoroutineScope,
    private val locked: () -> Boolean,
    private val otherTitles: suspend () -> Set<String>,
    private val random: Random = Random.Default,
    private val store: (title: String) -> Unit,
) {

    private val computeCodename = ComputeCodename()
    private val mutableTitle = MutableStateFlow("")
    val title: StateFlow<String> = mutableTitle.asStateFlow()

    /** The stored title, once the draft has loaded. */
    fun open(title: String) {
        mutableTitle.value = title
    }

    /** Takes [title], trimmed, when the draft isn't [locked] and it [isValidTitle] and differs from the current one. */
    fun rename(title: String) {
        val next = title.trim()
        if (locked() || !isValidTitle(next) || next == mutableTitle.value) return
        mutableTitle.value = next
        store(next)
    }

    /** A codename that no draft, this one included, is titled. */
    suspend fun rollCodename(): String = computeCodename(random, otherTitles() + mutableTitle.value)

    /** Stores a copy of the draft and reports its id; a draft gone from the store copies nothing. */
    fun duplicate(onCopied: (draftId: String) -> Unit) {
        scope.launch { copyDraft(draftId)?.let { onCopied(it.id) } }
    }

    /** Hands the draft's id to [onDelete], which leaves the Studio and deletes it with Undo. */
    fun delete(onDelete: (draftId: String) -> Unit) {
        onDelete(draftId)
    }

    companion object {
        /** A25: 1–60 characters once trimmed. */
        fun isValidTitle(title: String): Boolean = title.trim().length in 1..Draft.TITLE_MAX
    }
}
