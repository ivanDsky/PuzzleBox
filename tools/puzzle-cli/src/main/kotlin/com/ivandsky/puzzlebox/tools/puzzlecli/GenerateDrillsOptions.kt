package com.ivandsky.puzzlebox.tools.puzzlecli

/**
 * The `generate-drills` flags: [index] the directory holding each game's `<game>-index.txt`, [out] the directory the
 * `<game>-generated.txt` files go to, [seed] the run's random seed, [perTechnique] how many archive puzzles a technique
 * needs before it is no longer short and how many drills a short one is given, [maxAttempts] the candidate puzzles
 * tried per technique before the search gives up.
 */
class GenerateDrillsOptions(
    val index: String,
    val out: String,
    val seed: Long,
    val perTechnique: Int,
    val maxAttempts: Int,
) {
    companion object {
        const val DEFAULT_SEED: Long = 1L

        /** The spec's bound: at most five bundled drills per technique, and a technique with fewer archive puzzles is short. */
        const val DEFAULT_PER_TECHNIQUE: Int = 5

        /**
         * Mini Sudoku's rarest drills (claiming and hidden pair, with nothing harder in the solve) turn up in one candidate
         * in 4,000 to 10,000: five of them took 20,000 to 50,000 candidates (about 0.5 ms each). This leaves three times that.
         */
        const val DEFAULT_MAX_ATTEMPTS: Int = 150_000

        /**
         * The options [args] (everything after `generate-drills`) spell.
         *
         * @throws IllegalArgumentException with the reason when a flag is unknown, repeated, valueless or missing,
         *   `--seed` is not a whole number, `--per-technique` is not a whole number from 1 to
         *   [DEFAULT_PER_TECHNIQUE], or `--max-attempts` is not a positive whole number
         */
        fun parse(args: List<String>): GenerateDrillsOptions {
            val values = HashMap<String, String>()
            var at = 0
            while (at < args.size) {
                val flag = args[at]
                require(flag in FLAGS) { "unknown argument '$flag'" }
                require(flag !in values) { "$flag given twice" }
                values[flag] = args.getOrNull(at + 1) ?: throw IllegalArgumentException("$flag needs a value")
                at += 2
            }
            val seed = values["--seed"]?.let {
                requireNotNull(it.toLongOrNull()) { "--seed must be a whole number: '$it'" }
            } ?: DEFAULT_SEED
            val perTechnique = values["--per-technique"]?.let {
                val count = it.toIntOrNull()
                require(count != null && count in 1..DEFAULT_PER_TECHNIQUE) {
                    "--per-technique must be a whole number from 1 to $DEFAULT_PER_TECHNIQUE: '$it'"
                }
                count
            } ?: DEFAULT_PER_TECHNIQUE
            val maxAttempts = values["--max-attempts"]?.let {
                val count = it.toIntOrNull()
                require(count != null && count >= 1) { "--max-attempts must be a positive whole number: '$it'" }
                count
            } ?: DEFAULT_MAX_ATTEMPTS
            return GenerateDrillsOptions(
                index = requireNotNull(values["--index"]) { "--index is required" },
                out = requireNotNull(values["--out"]) { "--out is required" },
                seed = seed,
                perTechnique = perTechnique,
                maxAttempts = maxAttempts,
            )
        }

        private val FLAGS = setOf("--index", "--out", "--seed", "--per-technique", "--max-attempts")
    }
}
