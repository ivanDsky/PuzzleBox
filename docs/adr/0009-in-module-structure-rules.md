# ADR-0009 — In-module structure rules (`:konsist`)

Date: 2026-11-04 · Status: accepted · Source: structure-rules run (S0 S1 S2 S4 S5 P1–P7); P8 from the navigation-ux run (spec A5); S6 from the sudoku run (spec A12)

## Context
ADR-0008 guards the edges between modules. It says nothing about what is inside a module:
how many types a file holds, how long a file gets, where a Koin module or a ViewModel lives,
what a use case looks like. Prose rules for those drifted, and a worker can argue with prose.
A failing unit test cannot be argued with, so the rules became tests.

The suite lives in `:konsist`, a test-only `puzzlebox.jvm.library` module. It reads the repo's
Kotlin sources and build scripts as files with [Konsist](https://docs.konsist.lemonappdev.com/)
and has zero project dependencies. ADR-0008's `checkModuleGraph` guards it both ways: `:konsist`
may depend on no module, and no module may depend on `:konsist`. Run it with
`./gradlew :konsist:test`.

When a rule fails, the message ends with a link to the section of this file named after the rule
(`#s1`, `#p5`, …). Each section says what the rule is, where it looks, how it detects, what it
misses on purpose, and how to fix a hit.

## Decision

### Mechanics
- **Rules are pure functions of a `SourceTree`.** A rule implements `Rule` (`id`, `kinds`,
  `check(tree)`) and returns its subject count and its violations. A rule never calls a Konsist
  `assert*` and never decides a verdict itself. `RuleCheck.assertClean` sends every result
  through `judge`, which compares it with the rule's allowlist and returns the findings.
  `TestNamingTest` is the one test that bypasses `SourceTree`: it checks the naming of the rule
  tests themselves, and uses plain JUnit asserts.
- **`Rule.kinds` drives scope.** The default is production source sets. S0 and P5 also scan test
  source sets. A source set counts as test when its name starts with `test` or `androidTest`, or
  ends with `Test`. The same kinds decide which paths an allowlist entry may name.
- **S0 is a policy ban, not a style check.** Suppressing a rule would defeat the ratchet, so
  S0 has no allowlist and no escape hatch.
- **The zero-subject guard.** A rule that finds no subject in the real tree fails with
  `<ID>: scope empty`. A rule that matches nothing because its scope path is wrong cannot pass
  silently. The add-a-game check is the one place zero subjects is fine (a game has no P3, P4
  or S5 subjects).
- **The census.** `CensusTest` compares `SourceTree` with an independent directory walk, checks
  that every module contributes a file, that `konsist/` itself is out of scope, and that every
  scanned path is inside the Gradle test inputs.
- **The Gradle input superset.** `:konsist:test` declares `**/src/**/*.kt`, `**/*.gradle.kts`,
  `settings.gradle.kts` and `konsist/src/test/resources/**` as inputs (minus `build` output,
  `.gradle` and `build-logic/**/build`). Editing any source, build script or allowlist re-runs
  the rules, and build output never does. The census proves the tree stays inside this set.
- **Fixtures.** Each rule has a fixture tree under `konsist/src/test/resources/fixtures/<ID>`
  and a test that compares the subjects it judges there with an exact count and the violations
  it finds with an exact expected set (`assertFixture`). The count catches a fixture subject the
  rule silently stopped judging. The `add-a-game` fixture is ADR-0008's "Adding a game" recipe as files;
  every rule must report zero violations on it (`assertAddAGameClean`). Change that fixture
  only by a ledgered `Ruling:`.

### Allowlist policy
Existing violators sit in `konsist/src/test/resources/allowlists/<ID>.txt`, one entry per line:
`path` for a presence rule, `path ceiling` for a count rule (S1, S2, S5). Paths are relative to
the repo root with `/` separators. `#` at line start or after whitespace starts a comment; a `#`
inside a path is part of it. The list can only shrink. A rule with no
`allowlists/<ID>.txt` has an empty allowlist: every violation is new. `judge`
returns these findings:

| Finding | Meaning | Fix |
|---|---|---|
| new | a violating file has no entry | fix the code |
| grown | a count rule's value is above its ceiling | fix the code |
| slack | a count rule's value is below its ceiling | lower the ceiling |
| stale | the entry's file is out of scope or no longer violates | delete the entry |
| malformed | more than two tokens, an absolute path, `\`, a `..` segment, a bad or negative ceiling, a ceiling on a presence rule, or no ceiling on a count rule | correct the line |
| duplicate | the same path twice | delete the second line |

- Deleting an entry or lowering a ceiling is free.
- Adding an entry, raising a ceiling or renaming a path needs a ledgered `Ruling:` that says why
  the code cannot be fixed.
- **Worker contract.** Fix a violation by splitting or moving code. Rules, allowlists and
  fixtures are protected files: a worker does not edit them to make a test pass.

Entries on record at the time of writing: S1 64, S2 6, S4 25, S5 4, P1 6, P2 1, P5 4, P6 1.
S0, S6, P3, P4, P7 and P8 have none.

### Payload cross-reference
The `design-architecture` payload (`references/rules-architecture.md`) states four of these as
prose rules; the tests are their mechanical form and add no stricter bar:
- Rule 17, file discipline (one task per file, short, split by responsibility): S2 (300 lines).
- Rule 18, dispatchers injected, `Dispatchers.*` only in the composition root that builds the
  provider: P2.
- Rule 19, ViewModels expose read-only state: P3.
- Rule 20, one public top-level type per file (private helpers, extensions, typealiases and
  previews excepted): S1.

The other rules (S0, S4, S5, S6, P1, P4–P8) have no payload twin. Dependency direction between
modules stays in ADR-0008.

### Adding a rule
1. Add `konsist/src/test/kotlin/com/ivandsky/puzzlebox/konsist/rules/<ID><Name>.kt`, an
   `object` implementing `Rule`, and `<ID><Name>Test.kt` next to it. Test class and test
   function names start with the rule ID.
2. Add `allowlists/<ID>.txt` if there are existing violators, and `fixtures/<ID>/` with a
   violating tree.
3. Add a section named `## <ID>` to this file. The failure message links to its lowercase anchor.
4. There is no registry. A rule is a test class, so JUnit finds it.
5. A new game or Android module needs no suite edit. Modules are read from `settings.gradle.kts`
   and each module's `build.gradle.kts` (plugin ids from its `plugins { }` block). Values must be
   string literals, joined with `+` if needed; a variable or `$` template in an `include`, plugin
   id or `namespace` fails loudly rather than being skipped. An Android module's package root is
   its `namespace`, and an Android module without one fails loudly. A JVM module's package root is
   derived from its path with hyphens removed. A JVM module family the fallback does not know
   fails loudly.

## Rules
The rules below are numbered as in the spec. There is no S3.

## S0
No suppression of a structure rule.

- **Scope:** production and test source files.
- **Detection:** a `Suppress(...)` annotation in the file's code (comments and string contents
  removed), with any use-site target (`@file:`, `@get:`, `@field:`, `@param:` and the others) and
  also `@kotlin.Suppress` and the bracket form `@file:[A Suppress(...)]`. It is a violation when
  one of its string arguments starts with `konsist.` or with a rule ID (`S` or `P` plus digits).
  The argument list runs to the matching `)`, so a `)` inside an earlier string does not end it.
  The violation is the file.
- **Accepted misses:** other suppression ways (a name that does not look like a rule ID, a
  string constant passed as the argument) are not covered.
- **How to fix:** remove the suppression. Fix the code, or ask for a ledgered `Ruling:`.

## S1
One public type per file.

- **Scope:** production.
- **Detection:** count the public top-level classes, enums, annotations, interfaces and objects
  in a file, plus public `@Composable` functions that are not previews. A preview is defined once
  for S1 and S4 (see S4, Detection). Private, internal and protected declarations, plain
  functions and typealiases do not count. More than one is a violation, valued at the count (a
  ceiling rule).
- **Accepted misses:** it counts types, not responsibility. A file with one huge class that does
  five jobs passes. A file with two tiny related types is flagged.
- **How to fix:** move each extra type to its own file.

## S2
A production file has at most 300 lines.

- **Scope:** production, except files whose name ends in `Previews.kt`.
- **Detection:** physical lines, where a final newline ends the last line. Over 300 is a
  violation, valued at the line count (a ceiling rule).
- **Accepted misses:** blank and comment lines count. Splitting only to get under 300 passes.
- **How to fix:** split by responsibility (one job per file), not by line count.

## S4
Every public composable has a preview.

- **Scope:** production. Judged: top-level `@Composable` functions that are public or have no
  visibility modifier, and that are not themselves previews.
- **Detection:** a preview is a function with an annotation named `Preview` or one of the
  multipreviews `androidx.compose.ui.tooling.preview` ships (`PreviewLightDark`,
  `PreviewFontScale`, `PreviewScreenSizes`, `PreviewDynamicColors`), or with an annotation class
  in the scanned source that is itself annotated with one, followed transitively
  (multipreviews). S1 uses the same definition (`core/Previews.kt`). A composable is covered
  when its name is called (`Name(` or `Name {`) in the body of any preview function in the same
  module. One violation per file, listing the uncovered names.
- **Accepted misses:** coverage is by name and by module, not by file or by overload. A call
  anywhere in any preview of the module covers every composable with that name. Coverage is
  direct: a preview that calls a parent composable covers the parent only. A child the parent
  calls is flagged unless a preview also calls the child by name. Preview detection goes by
  annotation name, so any annotation with one of those names counts. A library multipreview not
  in the list above is not a preview.
  Ruling A7 widened the call match from `Name(` to `Name\s*[({]`, because wrappers such as
  `PuzzleBoxTheme` are only called with a trailing lambda. A `Name {` that is not a call counts
  as coverage.
- **How to fix:** add a preview that calls the composable (see the `preview-compose` skill).

## S5
No literal colors outside the design system.

- **Scope:** production, except files in package `com.ivandsky.puzzlebox.designsystem` and its
  sub-packages.
- **Detection:** a call to Compose's `Color(` whose first argument is a number (hex such as
  `0xFF112233`, decimal, float, with or without a name like `red =` and a suffix), in the file's
  code (comments and string contents removed). Space before `(` and a line break after it are
  allowed. A bare `Color(` counts only when the file imports `androidx.compose.ui.graphics.Color`
  or `androidx.compose.ui.graphics.*`; the qualified `androidx.compose.ui.graphics.Color(` always
  counts. A name that merely ends in `Color` (`MyColor(`) or is a member (`x.Color(`) is not
  matched. Valued at the number of calls in the file (a ceiling rule).
- **Accepted misses:** Compose `Color` imported under an alias, or reached through a typealias,
  is not matched. Any numeric Compose `Color(<number>` call matches, including ones that are not
  meant as a palette color. Colors built another way (`Color.Red`, `Color.hsv(...)`) are not
  matched.
- **How to fix:** use a design-system color token.

## S6
No infinite animation in games.

- **Scope:** production files of `:games:*` modules.
- **Detection:** the name `rememberInfiniteTransition`, `infiniteRepeatable` or
  `withInfiniteAnimationFrame` in the file's code (comments and string contents removed), as an
  import, a bare call or a qualified call. A name that only contains one of them
  (`myInfiniteRepeatable`) is not matched. The violation is the file.
- **Accepted misses:** an infinite loop built by hand (`while (true) { animate(...) }`, a
  `repeatable` with a huge count) or reached through a project wrapper in another module is not
  seen. Feature and core modules are not scanned.
- **How to fix:** animate only in response to input, with a finite spec (`tween`, `spring`,
  `repeatable(iterations = n)`).

## P1
Every production file's package is under its module's package root.

- **Scope:** production.
- **Detection:** the file's `package` must equal the module's package root or start with it
  plus a dot. The root is the module's Android `namespace`, or for a JVM module the fallback
  derived from its path. The dot keeps `zipx` from passing as a child of `zip`.
- **Accepted misses:** only the package is checked, not the directory.
- **How to fix:** move the file to a package under the module's root.

## P2
`Dispatchers` is referenced only in the dispatcher provider and in `:core:testing`.

- **Scope:** production, except `:core:testing` and files in `:core:common` whose package ends in
  `.common.dispatcher`.
- **Detection:** the text `kotlinx.coroutines.Dispatchers` or `Dispatchers.` in the file's code
  (comments and string contents removed; `${...}` templates are code). One pattern covers the
  import, the member import and the inline use.
- **Accepted misses:** `Dispatchers` reached through a typealias or an import alias is not seen.
- **How to fix:** inject a `DispatcherProvider`.

## P3
ViewModels are named `*ViewModel`, live in `:feature:<x>:impl`, and expose no public mutable state.

- **Scope:** production. Subjects: top-level (not nested) classes that extend `ViewModel` or whose
  name ends in `ViewModel`. Extending is followed through project base classes by simple name, so
  `class X : BaseViewModel()` extends `ViewModel` when `BaseViewModel` does. Only scanned classes
  count as bases: Konsist's own parent resolution reads every Kotlin file under the project root.
- **Detection:** a violation when the class extends `ViewModel` but its name does not end in
  `ViewModel`, or the reverse; when its module is not a feature `impl` module; or when it has a
  non-private property (primary-constructor `val`/`var` parameters included) that is a `var`, is
  declared with a type whose simple name starts with `Mutable` (qualified or nullable alike, so
  `kotlinx.coroutines.flow.MutableStateFlow<T>?` counts), or is initialized by a `Mutable...(` or
  `mutable...(` factory (so `MutableStateFlow(...)` and `mutableStateOf(...)` both count, per A11), or is typeless with an
  initializer or getter that is just the name of a class property that is a `var` or mutable by those
  rules. The name may be prefixed by `this.` and followed by receiver-returning calls
  (`val state = _state`, `val state get() = _state`, `val state = _state.also { }.apply { }`); an
  annotated getter is read through its body.
  A declared read-only type (`val state: StateFlow<T> = _state`) passes. One violation per file, joining the problems.
- **Accepted misses:** nested classes are not judged. Only `also` and `apply` count as
  receiver-returning; `_state.let { it }` or a helper that returns its argument passes. A base class
  is matched by simple name, so two project classes with the same name share their parents.
- **How to fix:** keep the `Mutable*` private and expose a read-only type; rename or move the class.

## P4
Use cases in `:core:domain` start with a verb and have one public `operator fun invoke`.

- **Scope:** production, module `:core:domain`, top-level public or default-visibility classes and
  objects, except data classes, data objects, enum classes, sealed classes and declarations in a
  `di` package.
- **Detection:** the name must start with one of Get, Observe, Compute, Submit, Save, Load, Delete,
  Update, Create, Sync, Validate, Record, Start, Finish, followed by an uppercase letter. The
  declaration must have exactly one public function named `invoke`, and that function must be an
  `operator fun`. An extra public non-operator `invoke` overload is a violation.
- **Accepted misses:** only `operator fun invoke` counts as the entry point; a use case with a
  differently named method fails. Data classes and objects, enums and sealed classes in use-case
  files are skipped (Ruling: they are result models such as `GameSummary`, not use cases), so one
  posing as a use case escapes. The verb list is closed.
- **How to fix:** rename the class with a listed verb and give it one `operator fun invoke`. A
  new verb is added to the rule by a ledgered `Ruling:`.

## P5
Naming conventions for types.

- **Scope:** production and test. Subjects: classes, objects (companion objects excluded) and
  interfaces.
- **Detection:**
  - no name ends in `Manager`, `Helper`, `Util`, `Utils` or `Impl` (classes, objects and interfaces);
  - a class or object named `Fake<X>` (an uppercase letter after `Fake`) must have a supertype
    whose name ends in `<X>`;
  - a class or object that implements a project interface whose name ends in `Repository`,
    `Source`, `Gateway`, `Store`, `Service` or `Provider` must be named `<Qualifier><Interface>`,
    longer than the interface name and ending in it.
  One violation per file, joining the problems.
- **Decision (intended, not misses):** objects are judged like classes, so
  `object InMemoryGameRepository : GameRepository` follows the same naming as a class. A type
  with several such interfaces passes if its name fits any one of them
  (`class RoomGameStore : GameRepository, Store`): a name carries only one suffix. Both are
  pinned by clean fixture cases.
- **Accepted misses:** interfaces get only the banned-suffix check, not the Fake or qualifier
  checks. `Fake` followed by a lowercase letter or nothing is not a Fake. Supertypes are
  compared by simple name.
- **How to fix:** rename the type (`OfflineFirstDailyRepository`, not `DailyRepositoryImpl`), or
  make the Fake implement the type it fakes.

## P6
Koin modules are declared in a `di` package.

- **Scope:** production.
- **Detection:** the file's code (comments and string contents removed) contains `module {`,
  `module(...) {`, `lazyModule {` or `lazyModule(...) {`, or the same call under a name the file
  imports `org.koin.dsl.module` or `org.koin.dsl.lazyModule` as (`import org.koin.dsl.module as m`),
  and its package does not end in `.di`. Exception: a `:games:*` module may keep its Koin module in
  the module's root package (ADR-0008, "Adding a game").
- **Accepted misses:** modules named `:games:<x>-ui` also count as game modules for the exception.
  A function that is called `module` and takes a lambda matches too. A Koin builder reached through
  a project wrapper function or a typealias is not matched.
- **How to fix:** move the declaration to a `di` package.

## P7
Android-free modules import nothing from Android.

- **Scope:** production files of modules using `puzzlebox.jvm.library`, plus `:core:domain`.
- **Detection:** a reference to `android.*`, `androidx.*`, `org.koin.android.*` or
  `org.koin.androidx.*` in the file's code (comments and string contents removed): an `import`, or
  an inline fully qualified use such as `android.util.Log.d(...)`. A name preceded by a dot or a
  word character (`com.example.android.x`) does not count. The violation lists the references.
- **Accepted misses:** Android types reached through a typealias or a project wrapper declared in
  another module are not seen. A local value named `android` followed by a member access matches.
- **How to fix:** remove the import; put the Android code in an Android module behind an interface.

## P8
Navigation keys carry only ids, ISO day strings and primitives (navigation-ux spec A5).

- **Scope:** production classes and objects with a direct `NavKey` supertype, plain or qualified. A
  type that holds NavKeys (such as `NavigationState`) is not a key.
- **Detection:** each property the key declares, primary-constructor `val`s included, must have a
  declared type whose simple name, `?` and type arguments removed, is `String`, `Int`, `Long`,
  `Boolean`, `Short`, `Byte`, `Char`, `Float` or `Double`. A `ByteArray`, a collection, a model type
  or a property with no declared type is a violation. The violation lists the properties.
- **Accepted misses:** a key reached through an intermediate supertype (`data class K : BaseKey`,
  `interface BaseKey : NavKey`) is not judged. A typealias to a banned type, or a project class
  named like a primitive (`String`), passes. A string that is not an id or an ISO day is not seen.
- **How to fix:** pass the id or the day as a string and load the rest in the destination's
  ViewModel. Saved state and process death restore the key, so it must stay small and stable.

## Consequences
- A structure regression fails `./gradlew :konsist:test` with the rule, the file, a fix hint and
  a link here. No review is needed to catch it.
- The existing violators are frozen, not hidden. Each allowlist can only shrink, and `slack` and
  `stale` findings force it to shrink as code is fixed.
- The rules are text and Konsist-declaration checks, not a compiler. Each accepted miss above is
  a known gap. A hole found later is fixed in the rule, with its fixture.
- A rule that finds a false positive is fixed in the rule, not worked around in the allowlist.
- `:konsist` is forbidden in the module graph (ADR-0008) so the suite cannot import production
  classes and start to depend on them.
