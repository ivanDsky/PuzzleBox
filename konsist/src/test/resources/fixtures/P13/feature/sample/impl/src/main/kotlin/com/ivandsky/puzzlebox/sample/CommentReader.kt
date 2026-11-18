package com.ivandsky.puzzlebox.sample

import com.ivandsky.puzzlebox.common.flags.ReleaseFlag.CommentOnly

// ReleaseFlag.CommentOnly used to gate this; a comment is not a reader.
fun commentOnly() = "ReleaseFlag.CommentOnly"
