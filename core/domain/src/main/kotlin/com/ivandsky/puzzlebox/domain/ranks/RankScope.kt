package com.ivandsky.puzzlebox.domain.ranks

/**
 * Whom a board ranks. [Global] is everyone on it. [Friends] ranks the viewer and their friends among themselves
 * (F18, which replaces A6's filter): places run 1..n and N is the friends who played, so a player's points and
 * Overall place differ between the scopes. See [amongFriends].
 */
enum class RankScope { Global, Friends }
