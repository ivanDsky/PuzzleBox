package com.ivandsky.puzzlebox.common.flags

/** Read as [ReleaseFlag.Qualified] and friends; a KDoc mention is not a reader. */
enum class ReleaseFlag(val label: String, val releaseDefault: Boolean) {
    Qualified(label = "Read as ReleaseFlag.Qualified", releaseDefault = true),
    FullyQualified(label = "Read fully qualified, with no import", releaseDefault = true),
    Imported(label = "Read through an entry import", releaseDefault = true),
    Aliased(label = "Read through an aliased entry import", releaseDefault = true),
    ClassAliased(label = "Read through an alias of ReleaseFlag", releaseDefault = true),
    Starred(label = "Read through ReleaseFlag.*", releaseDefault = true),
    CommentOnly(label = "Named only in a comment, a string and an unused import", releaseDefault = true),
    DebugOnly(label = "Read only in a debug source set", releaseDefault = true),
    TestOnly(label = "Read only in a test source set", releaseDefault = true),
    TestingOnly(label = "Read only in :core:testing main sources", releaseDefault = true),
    SelfOnly(label = "Read only inside ReleaseFlag.kt", releaseDefault = true),

    /** releaseDefault = false in a comment does not make an entry dark. */
    PositionalDark("Dark, but not by a named releaseDefault = false", false),
    DarkUnread(label = "Dark and not read yet, like a trunk feature's ticket 01", releaseDefault = false),
    ;

    companion object {
        internal val selfRead = ReleaseFlag.SelfOnly
    }
}
