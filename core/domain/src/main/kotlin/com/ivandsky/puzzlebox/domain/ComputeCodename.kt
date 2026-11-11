package com.ivandsky.puzzlebox.domain

import kotlin.random.Random

/** Picks a random family-safe "Adjective Noun" draft title (e.g. "Amber Falcon") that no existing draft already has. */
class ComputeCodename {

    /**
     * A random codename not in [taken]: [random] picks a start, the next free combination from there
     * wins, so a given [random] and [taken] always give the same name. With every combination taken
     * the start gets the smallest free " 2", " 3", … suffix.
     */
    operator fun invoke(random: Random, taken: Set<String>): String {
        val total = adjectives.size * nouns.size
        val start = random.nextInt(total)
        return (0 until total).asSequence().map { name((start + it) % total) }.firstOrNull { it !in taken }
            ?: generateSequence(2) { it + 1 }.map { "${name(start)} $it" }.first { it !in taken }
    }

    private fun name(index: Int): String = "${adjectives[index / nouns.size]} ${nouns[index % nouns.size]}"

    private companion object {
        val adjectives = listOf(
            "Amber", "Azure", "Bold", "Brisk", "Bright", "Calm", "Clever", "Cobalt", "Coral", "Crimson",
            "Dusty", "Eager", "Emerald", "Gentle", "Golden", "Hazel", "Indigo", "Ivory", "Jade", "Keen",
            "Lively", "Lunar", "Maple", "Mellow", "Misty", "Nimble", "Olive", "Pearl", "Quiet", "Rosy",
            "Rustic", "Scarlet", "Silver", "Sleek", "Snowy", "Sunny", "Swift", "Teal", "Velvet", "Violet",
        )

        val nouns = listOf(
            "Acorn", "Anchor", "Badger", "Basket", "Beacon", "Birch", "Canyon", "Clover", "Comet", "Cricket",
            "Daisy", "Falcon", "Feather", "Fern", "Fox", "Glacier", "Harbor", "Heron", "Kettle", "Lantern",
            "Lark", "Lighthouse", "Marble", "Meadow", "Otter", "Panda", "Pebble", "Pine", "Quill", "Raven",
            "River", "Robin", "Sparrow", "Thistle", "Tulip", "Walnut", "Willow", "Wolf", "Wren", "Zephyr",
        )
    }
}
