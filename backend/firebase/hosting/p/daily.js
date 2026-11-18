// PuzzleBox no-app page logic for a daily link: read /p/<game>/<yyyy-MM-dd> exactly like the
// app's DailyLink.parse (id a-z0-9 and case-insensitive, lower-cased; a strict, calendar-valid
// date; one optional trailing slash) and fill in the heading and the date line.
// Parity with DailyLink.parse is enforced by ../hosting-test/daily.test.mjs and
// DailyPageFilesTest over ../hosting-test/daily-link-vectors.json.

const PATH_PREFIX = "/p/";
const DEFAULT_HEADING = "A PuzzleBox daily puzzle";
const GAME_NAMES = {
  queens: "Queens",
  tango: "Tango",
  zip: "Zip",
  minisudoku: "Mini Sudoku",
  strands: "Strands",
};
// A fixed list, not Intl: the text must be the same on every phone and in the tests.
const MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];

function isCalendarDay(text) {
  if (!/^[0-9]{4}-[0-9]{2}-[0-9]{2}$/.test(text)) return false;
  const [year, month, day] = text.split("-").map(Number);
  const date = new Date(Date.UTC(year, month - 1, day));
  return date.getUTCFullYear() === year && date.getUTCMonth() === month - 1 && date.getUTCDate() === day;
}

/**
 * Reads a request path (e.g. location.pathname) as a daily link.
 * Returns { game, day } for a valid path, null for the invalid state.
 */
export function parseDaily(path) {
  if (!path.startsWith(PATH_PREFIX)) return null;
  let rest = path.slice(PATH_PREFIX.length);
  if (rest.endsWith("/")) rest = rest.slice(0, -1);
  const parts = rest.split("/");
  if (parts.length !== 2) return null;
  const game = parts[0].toLowerCase();
  if (!/^[a-z0-9]+$/.test(game)) return null;
  if (!isCalendarDay(parts[1])) return null;
  return { game, day: parts[1] };
}

/** "PuzzleBox · Zip" for a shipped game; the default heading for any other id. */
export function headingFor(game) {
  const name = Object.hasOwn(GAME_NAMES, game) ? GAME_NAMES[game] : null;
  return name === null ? DEFAULT_HEADING : `PuzzleBox · ${name}`;
}

/** "2026-11-18" → "Nov 18, 2026". */
export function dateText(day) {
  const [year, month, date] = day.split("-").map(Number);
  return `${MONTHS[month - 1]} ${date}, ${year}`;
}

function wirePage() {
  const result = parseDaily(location.pathname);
  if (result === null) {
    document.body.classList.add("invalid");
    return;
  }
  document.getElementById("heading").textContent = headingFor(result.game);
  document.getElementById("date").textContent = dateText(result.day);
}

if (typeof document !== "undefined") {
  wirePage();
}
