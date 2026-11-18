package com.ivandsky.puzzlebox.a

import com.ivandsky.puzzlebox.domain.GetAppTested
import com.ivandsky.puzzlebox.domain.GetAppToo
import com.ivandsky.puzzlebox.domain.GetApiImpl
import com.ivandsky.puzzlebox.domain.GetCommented
import com.ivandsky.puzzlebox.domain.GetCoreToo
import com.ivandsky.puzzlebox.domain.GetForeignTested
import com.ivandsky.puzzlebox.domain.GetShared
import com.ivandsky.puzzlebox.domain.GetSingle
import com.ivandsky.puzzlebox.domain.LeafThing
import com.ivandsky.puzzlebox.domain.ranks.isTopRank

class AScreen(
    val single: GetSingle,
    val shared: GetShared,
    val appToo: GetAppToo,
    val coreToo: GetCoreToo,
    val foreignTested: GetForeignTested,
    val appTested: GetAppTested,
    val apiImpl: GetApiImpl,
    val commented: GetCommented,
    val leaf: LeafThing,
) {
    fun top(rank: Int) = rank.isTopRank()

    fun inline() = com.ivandsky.puzzlebox.domain.GetInline()()
}
