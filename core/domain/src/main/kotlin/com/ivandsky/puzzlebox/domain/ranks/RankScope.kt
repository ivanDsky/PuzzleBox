package com.ivandsky.puzzlebox.domain.ranks

/**
 * Which players a board list shows (A6). [Friends] only filters the list: points and N always
 * come from the global board, so a player's Overall points match in both scopes.
 */
enum class RankScope { Global, Friends }
