package com.ivandsky.puzzlebox.game.linkedin

/**
 * One game's LinkedIn play record for `updateGameState`: its game-specific states keyed by LinkedIn state key
 * (Zip: zipGamePathBinding, zipNumBacktracksBinding), the payload arguments that point at them
 * (Zip: playRecord → zipGamePathBinding, numBacktracks → zipNumBacktracksBinding), and whether the solve was mistake-free.
 * [opening] is the first-move play sent before the win; a game with none is a single win call.
 */
data class LinkedInPlay(
    val states: Map<String, LinkedInPlayValue>,
    val payloadKeys: Map<String, String>,
    val mistakeFree: Boolean,
    val opening: LinkedInOpening? = null,
)
