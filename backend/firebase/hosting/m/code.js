// PuzzleBox no-app page logic: normalise a /m/<CODE> path exactly like the app's
// ShareCode.parse (case-insensitive; '-' and spaces ignored; O→0, I/L→1;
// Crockford base32; exactly 6 characters) and wire up the copy button.
// Parity with ShareCode.parse is enforced by ../hosting-test/code.test.mjs over
// ../hosting-test/share-code-vectors.json.

const ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
const LENGTH = 6;
const PATH_PREFIX = "/m/";

function canonicalSymbol(c) {
  const upper = c >= "a" && c <= "z" ? String.fromCharCode(c.charCodeAt(0) - 32) : c;
  const mapped = upper === "O" ? "0" : upper === "I" || upper === "L" ? "1" : upper;
  return ALPHABET.includes(mapped) ? mapped : null;
}

/**
 * Normalises a request path (e.g. location.pathname) to the shared code.
 * Returns { code, display } for a valid path, null for the invalid state.
 */
export function normalize(path) {
  let rest = path;
  if (rest.startsWith(PATH_PREFIX)) rest = rest.slice(PATH_PREFIX.length);
  if (rest.endsWith("/")) rest = rest.slice(0, -1);
  let canonical = "";
  for (const c of rest) {
    if (c === "-" || c === " ") continue;
    const symbol = canonicalSymbol(c);
    if (symbol === null) return null;
    if (canonical.length === LENGTH) return null;
    canonical += symbol;
  }
  if (canonical.length !== LENGTH) return null;
  return {
    code: canonical,
    display: canonical.slice(0, 3) + "-" + canonical.slice(3),
  };
}

function showInvalid() {
  document.body.classList.add("invalid");
}

function wirePage() {
  const result = normalize(location.pathname);
  if (result === null) {
    showInvalid();
    return;
  }
  document.getElementById("code").textContent = result.display;
  const button = document.getElementById("copy");
  button.hidden = false;
  button.addEventListener("click", () => {
    navigator.clipboard.writeText(result.code);
    button.textContent = "Copied";
    setTimeout(() => {
      button.textContent = "Copy code";
    }, 1500);
  });
}

if (typeof document !== "undefined") {
  wirePage();
}
