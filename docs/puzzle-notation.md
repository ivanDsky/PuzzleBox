# Puzzle notation and `puzzle-cli`

This page is the reference for writing a PuzzleBox puzzle as plain text and checking it. It is
for authors and for AI agents. The text is the same text the app imports, and the command-line
tool gives the same verdict the app will give.

Every example below is a real test fixture. A build test (`NotationDocTest`) checks that each
`puzzlebox` block is identical to one fixture file under `games/<game>/src/test/resources/notation/`,
and that every fixture file appears here exactly once. To change an example, change its fixture.

## Text format

Every game uses the same outer shape:

```
puzzlebox <game> v<n>
<body>
```

- **Header.** The first line that is not blank and not a `#` comment must read
  `puzzlebox <game> v<n>`: single spaces, a game id, and a version number `n` of 1 or more with
  no leading zero. The game ids are `queens`, `zip`, `tango`, `minisudoku`, `sudoku` and
  `strands`.
- **Version.** Every game is at `v1`. A text with a higher version than the build reads is
  rejected as unsupported, so a newer file never gets misread by an older build.
- **Comments.** A line whose first non-space character is `#` is ignored, anywhere in the text.
  Every example here starts with a `# expect:` comment. The app and the CLI skip it; the tests
  read it. It names the verdict the fixture must produce, followed by the publish blocks, if any.
- **Blank lines** are ignored in every game.
- **Size cap.** The whole text may be at most 64 KB (65,536 bytes in UTF-8). Longer text is
  rejected before anything is parsed, and the error points at the first character past the cap.
- **The board, not its answer.** Queens answer crowns and Sudoku solved digits are never part of
  the text. Two games let the author write down the route they drew: Zip's `path` line and
  Strands' `word` cells. They are part of the draft, and the verdict still comes from the solver.

### Line and column numbers

An error names a line and a column, both counted from 1 over the whole text exactly as you wrote
it, so comment lines, blank lines and the header all count. The header is line 1 only when nothing
comes before it. Take this text:

```
# my board

puzzlebox queens v1
0011
0011
0x11
0011
```

Its error is at line 6, column 2: the `x`. Where a game splits a line into whitespace-separated
words (Zip, Strands), the column is where the offending word starts. A grid cell is written
`r,c`: row `r` counted from the top and column `c` from the left, both starting at 1.

## Queens (`queens`)

The body is N rows of N characters, with N from 4 to 12. Each character is a region id, `0` to `9`
and then `a` and `b`, below N, or `.` for a cell that is not painted yet. Nothing else may appear
inside a row. Trailing spaces are ignored.

A Queens board is Unique when exactly one placement of one queen per row, column and region
exists. A region that is cut into separate pieces is a publish block, `split-region`.

A unique board:

```puzzlebox
# expect: unique
puzzlebox queens v1
00001
00031
42033
43333
43333
```

The same board with the top-left cell not painted yet is still being drawn, so its verdict is
`incomplete`:

```puzzlebox
# expect: incomplete
puzzlebox queens v1
.0001
00031
42033
43333
43333
```

A board with more than one solution. It also has a region in two pieces, so it carries the
`split-region` block after the verdict:

```puzzlebox
# expect: multiple split-region
puzzlebox queens v1
00111
02211
02333
44433
44442
```

## Zip (`zip`)

The body is N rows of N cells, with N from 5 to 8, then optional `wall` and `path` lines, in any
order. Cells in a row are separated by whitespace.

- A cell is `.` or a waypoint number. The numbers are exactly 1 to k, each used once.
- `wall r,c right` or `wall r,c down` puts a wall on the right or bottom edge of that cell. A
  wall on the outer border is an error, and so is the same wall twice.
- `path r,c r,c …` is the path the author drew, cell by cell. Steps are up, down, left or right;
  no cell repeats; no wall sits between two steps. A path ties the numbers to it: the first
  cell is waypoint 1, the last is waypoint k, and the numbers between come in path order. A text
  that breaks this is an error at the `path` line, column 1.

A `path` that stops before it has covered every cell makes the board `incomplete`. A board with
no `path` needs at least two numbers; with fewer it is `incomplete` too.

A board with no numbers and no path is `incomplete`:

```puzzlebox
# expect: incomplete
puzzlebox zip v1
. . . . . .
. . . . . .
. . . . . .
. . . . . .
. . . . . .
. . . . . .
```

Two numbers and nothing else leave many routes, so the verdict is `multiple`:

```puzzlebox
# expect: multiple
puzzlebox zip v1
1 . . . .
. . . . .
. . . . .
. . . . .
. . . . 2
```

Four walls and four numbers give one route, so this board is `unique`:

```puzzlebox
# expect: unique
puzzlebox zip v1
1 . . . . .
. . . . . .
. . . . . .
2 . . . . .
. . . . . .
4 . . . . 3
wall 2,3 down
wall 2,5 down
wall 4,3 down
wall 4,5 down
```

The same board with its path written out:

```puzzlebox
# expect: unique
puzzlebox zip v1
1 . . . . .
. . . . . .
. . . . . .
2 . . . . .
. . . . . .
4 . . . . 3
wall 2,3 down
wall 2,5 down
wall 4,3 down
wall 4,5 down
path 1,1 1,2 1,3 1,4 1,5 1,6 2,6 2,5 2,4 2,3 2,2 2,1 3,1 3,2 3,3 3,4 3,5 3,6 4,6 4,5 4,4 4,3 4,2 4,1 5,1 5,2 5,3 5,4 5,5 5,6 6,6 6,5 6,4 6,3 6,2 6,1
```

## Tango (`tango`)

The body is 6 rows of 6 characters: `S` (sun), `M` (moon) or `.` (empty). No spaces inside a row.
After the rows, each marker is one line: `= r,c right|down` for two cells that must match, or
`x r,c right|down` for two cells that must differ. The marker sits on the right or bottom edge of
cell `r,c`. A border edge is an error, and so is a second marker on one edge.

A unique board that the hint ladder can finish (`difficulty` is `hard`, no blocks):

```puzzlebox
# expect: unique
puzzlebox tango v1
SMSMSM
......
SMSMSM
......
SMSMSM
......
x 1,1 right
x 1,1 down
```

A board with one answer that the hint ladder cannot reach step by step. The verdict is `unique`
and the publish block `needs-guessing` follows it, so the board is not publishable:

```puzzlebox
# expect: unique needs-guessing
puzzlebox tango v1
S....S
.M..S.
...SM.
.....S
..M...
....M.
```

Three suns in a row break the rules before any search, so the verdict is `none` and the three
cells are listed in `conflicts`:

```puzzlebox
# expect: none
puzzlebox tango v1
SSS...
......
......
......
......
......
= 1,1 right
```

An empty board with one marker has many answers, so it is `multiple`:

```puzzlebox
# expect: multiple
puzzlebox tango v1
......
......
......
......
......
......
= 1,1 right
```

## Mini Sudoku and Sudoku (`minisudoku`, `sudoku`)

The game id in the header picks the size: `minisudoku` is 6 rows of 6 cells with digits 1 to 6;
`sudoku` is 9 rows of 9 cells with digits 1 to 9. A cell is a digit, or `.` or `0` for empty.
Spaces inside a row are ignored. The 9 by 9 game exists only in debug builds of the app; a
release build answers `sudoku` with "unknown game".

A unique Mini Sudoku:

```puzzlebox
# expect: unique
puzzlebox minisudoku v1
..3.5.
.2.4.6
1.....
.....2
2.4.1.
.1.3..
```

Too few givens leave several answers, so this one is `multiple`:

```puzzlebox
# expect: multiple
puzzlebox minisudoku v1
4.....
.2.4..
...5..
....4.
2.....
....2.
```

Two 1s in one box break a rule. The verdict is `none`, and `conflicts` lists `[1,1]` and `[2,2]`:

```puzzlebox
# expect: none
puzzlebox minisudoku v1
1.....
.1.3..
......
......
5..2..
..6..3
```

A unique 9 by 9 board:

```puzzlebox
# expect: unique
puzzlebox sudoku v1
53..7....
6..195...
.98....6.
8...6...3
4..8.3..1
7...2...6
.6....28.
...419..5
....8..79
```

## Strands (`strands`)

The body is, in order:

1. One line `clue:` followed by the clue, which is the rest of that line, so a clue is a single
   line. It may be empty.
2. 8 rows of 6 characters. Each is a letter `A` to `Z` (lower case is read as upper case) or
   `.` for a blank cell. No spaces inside a row.
3. One `word` line per path: `word <WORD> r,c r,c …`. The cells are required. Each step goes to
   one of the 8 neighbouring cells, and no cell appears in two paths. The path must spell the
   word. Words use letters `A` to `Z` only.

A `*` before the word, as in `*UTENSILS`, marks the spangram; at most one path may carry it. A
`?` in place of the word is a path that has no word yet, so the board is `incomplete`. A starred
`?` (`*?`) is an unnamed spangram, and it is accepted too.

A unique board. The clue gate passes, so this is publishable:

```puzzlebox
# expect: unique
puzzlebox strands v1
clue: In the kitchen
UWRKKE
THSEWS
REIGNT
ENSAEO
TSLEEL
AIPUAR
RLLTLP
SGEDAS
word *UTENSILS 1,1 2,1 3,2 4,2 5,2 6,2 7,2 8,1
word SPATULA 8,6 7,6 6,5 7,4 6,4 5,3 4,4
word WHISK 1,2 2,2 3,3 2,3 1,4
word LADLE 7,5 8,5 8,4 7,3 8,3
word GRATER 8,2 7,1 6,1 5,1 4,1 3,1
word TONGS 3,6 4,6 3,5 3,4 4,3
word PEELER 6,3 5,4 4,5 5,6 5,5 6,6
word SKEWER 2,6 1,5 1,6 2,5 2,4 1,3
```

The same board with TONGS left as `?` is `incomplete`:

```puzzlebox
# expect: incomplete
puzzlebox strands v1
clue: In the kitchen
UWRKKE
THSEWS
REIGNT
ENSAEO
TSLEEL
AIPUAR
RLLTLP
SGEDAS
word *UTENSILS 1,1 2,1 3,2 4,2 5,2 6,2 7,2 8,1
word SPATULA 8,6 7,6 6,5 7,4 6,4 5,3 4,4
word WHISK 1,2 2,2 3,3 2,3 1,4
word LADLE 7,5 8,5 8,4 7,3 8,3
word GRATER 8,2 7,1 6,1 5,1 4,1 3,1
word ? 3,6 4,6 3,5 3,4 4,3
word PEELER 6,3 5,4 4,5 5,6 5,5 6,6
word SKEWER 2,6 1,5 1,6 2,5 2,4 1,3
```

Here the last `E` of `MATE` and of `PATE` can trade places, so the verdict is `multiple` and
`differingCells` lists `[5,4]` and `[6,4]`:

```puzzlebox
# expect: multiple
puzzlebox strands v1
clue: Kitchen and pantry
ROFKSU
KLEEET
EEWPLE
RERADN
MATELS
PATEIR
HIKAEL
WSRGTS
word *UTENSILS 1,6 2,6 3,6 4,6 5,6 6,5 7,6 8,6
word MATE 5,1 5,2 5,3 5,4
word PATE 6,1 6,2 6,3 6,4
word WHISK 8,1 7,1 7,2 8,2 7,3
word LADLE 5,5 4,4 4,5 3,5 2,5
word GRATER 8,4 8,3 7,4 8,5 7,5 6,6
word PEELER 3,4 2,3 3,2 2,2 3,1 4,1
word SKEWER 1,5 1,4 2,4 3,3 4,2 4,3
word FORK 1,3 1,2 1,1 2,1
```

## The command line

Build it once, then run it without Gradle:

```
./gradlew :tools:puzzle-cli:installDist
tools/puzzle-cli/build/install/puzzle-cli/bin/puzzle-cli validate board.txt
tools/puzzle-cli/build/install/puzzle-cli/bin/puzzle-cli solve board.txt
```

`validate <file>` prints one JSON line. `solve <file>` prints the same line and, for a `unique`
board, adds the solution. The tool reads one file, makes no network calls and holds no
credentials. Its analysis stops after 30 seconds and reports `undetermined`.

### The JSON line (schema 1)

| Field | Meaning |
| --- | --- |
| `schema` | Always `1`. |
| `game` | The header's game id. Empty when the text has no usable header or fails to parse. |
| `verdict` | `unique`, `multiple`, `none`, `incomplete`, `undetermined` or `malformed`. |
| `difficulty` | `easy`, `medium`, `hard` or `expert`, only for a `unique` board of a game that rates difficulty; otherwise `null`. |
| `needsGuessing` | Only for `unique`: `true` when the hint ladder cannot finish the board. Otherwise `null`. |
| `differingCells` | For `multiple`: the `[row, col]` cells where two answers differ. |
| `conflicts` | For `none`: the `[row, col]` cells that already break a rule. |
| `blocks` | The ids of the board-level publish gates that fail (below). |
| `errors` | For `malformed`: one `{"line", "col", "message"}`. Empty otherwise. |
| `solution` | Only for `solve` on a `unique` board. The game's own list of values: for Queens, the cell index of each row's queen. Left out otherwise. |

Every `[row, col]` pair counts from 1, like the cells in the notation.

A unique board, `validate`:

```json
{"schema":1,"game":"queens","verdict":"unique","difficulty":"easy","needsGuessing":false,"differingCells":[],"conflicts":[],"blocks":[],"errors":[]}
```

The same board, `solve`:

```json
{"schema":1,"game":"queens","verdict":"unique","difficulty":"easy","needsGuessing":false,"differingCells":[],"conflicts":[],"blocks":[],"errors":[],"solution":[2,9,11,18,20]}
```

A board with several answers:

```json
{"schema":1,"game":"queens","verdict":"multiple","difficulty":null,"needsGuessing":null,"differingCells":[[1,1],[1,3],[2,1],[2,4],[3,2],[3,4],[4,2],[4,5],[5,3],[5,5]],"conflicts":[],"blocks":["split-region"],"errors":[]}
```

A text that does not parse:

```json
{"schema":1,"game":"","verdict":"malformed","difficulty":null,"needsGuessing":null,"differingCells":[],"conflicts":[],"blocks":[],"errors":[{"line":6,"col":2,"message":"unexpected character 'x'"}]}
```

### Publish blocks

A block is a board-level reason the puzzle may not be published, even when it has one answer.

| Game | Block ids |
| --- | --- |
| `queens` | `split-region`, `needs-guessing` |
| `zip` | none |
| `tango`, `minisudoku`, `sudoku` | `needs-guessing` |
| `strands` | `strands.clue-length` (the clue is blank or over 40 characters), `strands.word-shape` (a word is not 4 to 24 letters), `strands.duplicate-word`, `strands.spangram-edges` (the spangram does not reach two opposite edges), `blocklisted` |

Title length, the daily cap and the test-solve step apply in the app only. The text has none of them.

### Exit codes

| Code | Meaning |
| --- | --- |
| `0` | Publishable: the verdict is `unique` and `blocks` is empty. |
| `1` | The text parsed but the board is not publishable: any other verdict, or any block. |
| `2` | `malformed`: text too large, bad or missing header, unknown game, unsupported version, or a parse error. The JSON line has the error. |
| `64` | Usage: wrong arguments or an unreadable file. A message goes to standard error and no JSON is printed. |

"Publishable" means exit code 0. It is the signal that the board will pass the board gates when a
person imports it into the app.

## In the app

There are two ways to get the same text into the app. Both run the same import as the CLI, so the
verdict matches. Both stay off until their release flags flip: `TextImport` for the first and
`ShareTarget` for the second.

- **Import from text.** On the Create tab, choose Import from text, paste the text and tap
  Import. A text that parses is saved as a draft marked as imported and opens in the editor
  with its live verdict. A text that fails leaves the sheet open with one line, such as
  `Line 6: unexpected character 'x'`. Nothing is saved.
- **Share sheet.** From another app, share the text to PuzzleBox (plain text). The app opens on
  Create and runs the same import. On success the draft opens in the editor; on failure the
  import sheet opens with the error. A shared text over 64 KB is rejected before parsing.
