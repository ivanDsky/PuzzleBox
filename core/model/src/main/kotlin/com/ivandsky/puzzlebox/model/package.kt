/**
 * PuzzleBox's shared domain model — one dependency-free home for the types that more
 * than one module must agree on.
 *
 * WHY a single flat package (`com.ivandsky.puzzlebox.model`): these types are the
 * vocabulary of the domain seams (game identity, user preferences),
 * not any module's implementation detail. One package keeps imports stable no matter
 * which module historically owned a type, and makes "what is shared" answerable by
 * listing one directory.
 *
 * WHY a pure JVM module: :core:game (pure Kotlin contracts) and :core:data (Android,
 * Room, Retrofit, DataStore) both compile against it, so it must depend on nothing —
 * no Android, no Compose, no serialization, no coroutines. The dependency direction is
 * one-way: everything may depend on :core:model; :core:model depends on nothing.
 *
 * Membership rule: a type lands here only when two or more modules need it at a public
 * seam AND it carries no framework weight. Module-internal shapes (Room entities, wire
 * DTOs, game variants) stay in their owning module.
 *
 * Current members: [GameId], [ThemeMode].
 */
package com.ivandsky.puzzlebox.model
