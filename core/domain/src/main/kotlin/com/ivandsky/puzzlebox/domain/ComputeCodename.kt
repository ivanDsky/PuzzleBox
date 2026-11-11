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

    companion object {
        internal val adjectives = listOf(
            "Amber", "Azure", "Bold", "Brisk", "Bright", "Calm", "Clever", "Cobalt",
            "Coral", "Crimson", "Dusty", "Eager", "Emerald", "Gentle", "Golden", "Hazel",
            "Indigo", "Ivory", "Jade", "Keen", "Lively", "Lunar", "Maple", "Mellow",
            "Misty", "Nimble", "Olive", "Pearl", "Quiet", "Rosy", "Rustic", "Scarlet",
            "Silver", "Sleek", "Snowy", "Sunny", "Swift", "Teal", "Velvet", "Violet",
            "Alpine", "Autumn", "Breezy", "Cheerful", "Copper", "Cosmic", "Crisp", "Cozy",
            "Dapper", "Dewy", "Distant", "Dreamy", "Earthy", "Faded", "Fancy", "Fuzzy",
            "Glossy", "Grand", "Happy", "Hidden", "Humble", "Icy", "Jolly", "Kind",
            "Lavender", "Leafy", "Lucky", "Mighty", "Mossy", "Navy", "Noble", "Orange",
            "Plucky", "Polished", "Proud", "Quick", "Radiant", "Rapid", "Ruby", "Sandy",
            "Shady", "Shiny", "Smooth", "Spry", "Steady", "Stormy", "Sturdy", "Sunlit",
            "Tidy", "Tiny", "Twilight", "Vivid", "Warm", "Wild", "Wise", "Witty",
            "Woolly", "Zesty", "Chilly", "Frosty", "Peachy", "Merry",
        )

        internal val nouns = listOf(
            "Acorn", "Anchor", "Badger", "Basket", "Beacon", "Birch", "Canyon", "Clover",
            "Comet", "Cricket", "Daisy", "Falcon", "Feather", "Fern", "Fox", "Glacier",
            "Harbor", "Heron", "Kettle", "Lantern", "Lark", "Lighthouse", "Marble", "Meadow",
            "Otter", "Panda", "Pebble", "Pine", "Quill", "Raven", "River", "Robin",
            "Sparrow", "Thistle", "Tulip", "Walnut", "Willow", "Wolf", "Wren", "Zephyr",
            "Apple", "Arrow", "Bridge", "Button", "Candle", "Castle", "Cliff", "Cloud",
            "Compass", "Cottage", "Crow", "Dolphin", "Dune", "Eagle", "Ember", "Fiddle",
            "Finch", "Flute", "Forest", "Garden", "Hammock", "Hawk", "Hill", "Island",
            "Jacket", "Kite", "Ladder", "Lemon", "Lily", "Mirror", "Moss", "Mountain",
            "Nest", "Orchard", "Owl", "Paddle", "Parrot", "Peach", "Pond", "Puffin",
            "Rabbit", "Rainbow", "Reef", "Ribbon", "Rocket", "Saddle", "Sailboat", "Seal",
            "Shell", "Spruce", "Squirrel", "Stream", "Sunrise", "Swan", "Teapot", "Tiger",
            "Trail", "Turtle", "Valley", "Violin", "Waffle", "Wagon", "Whale", "Windmill",
            "Yarn", "Zebra", "Cabin", "Cactus", "Lagoon", "Pepper",
        )
    }
}
