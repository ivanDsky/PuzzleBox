#!/usr/bin/env bash
# Runs every case in cases.json against the local firestore.rules on the Firebase Rules
# Test API (projects/<project>:test). Nothing is deployed. Authenticates as the
# firebase-tools login; exits non-zero on any case whose verdict differs from `expect`.
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
rules="$here/../firestore.rules"
cases="$here/cases.json"
tokens="$HOME/.config/configstore/firebase-tools.json"

command -v firebase >/dev/null || { echo "firebase-tools is not installed" >&2; exit 2; }
command -v jq >/dev/null || { echo "jq is not installed" >&2; exit 2; }

# Any authenticated firebase-tools call refreshes the stored access token when it has expired.
firebase projects:list --json >/dev/null 2>&1 || { echo "firebase-tools is not logged in (firebase login)" >&2; exit 2; }
token="$(jq -r '.tokens.access_token // empty' "$tokens")"
[ -n "$token" ] || { echo "no firebase-tools access token in $tokens" >&2; exit 2; }
project="$(jq -r '.project' "$cases")"
expected="$(jq '.cases | length' "$cases")"

# The Test API refuses a request with more than about 240 cases ("Request has too many test cases"),
# so the cases go up in batches and the results are joined back in case order.
batch=200
results='[]'
for ((offset = 0; offset < expected; offset += batch)); do

# A case's documents may be {"$fixture": name, ...overrides} and a string may be
# {"$repeat": [text, n]}; both expand here, before the request is built.
suite="$(jq --rawfile source "$rules" --argjson offset "$offset" --argjson batch "$batch" '
  .fixtures as $fx
  | .time as $time
  | def expand:
      if type == "object" and has("$fixture") then
        ($fx[."$fixture"] | expand) + (del(."$fixture") | map_values(expand))
      elif type == "object" and has("$repeat") then
        ."$repeat"[0] * ."$repeat"[1]
      elif type == "object" then map_values(expand)
      elif type == "array" then map(expand)
      else . end;
    def doc: "/databases/(default)/documents/" + .;
    def mock:
      { function: .fn,
        args: [{ exactValue: (.path | doc) }],
        result: (if has("value") then { value: .value }
                 elif .missing then { undefined: {} }
                 else { value: { data: (.data | expand) } } end) };
  {
    source: { files: [{ name: "firestore.rules", content: $source }] },
    testSuite: { testCases: [ .cases[$offset:$offset + $batch][] | {
      expectation: (.expect | ascii_upcase),
      request: ({ path: (.path | doc), method: .method, time: $time }
        + (if .auth then { auth: { uid: .auth } } else {} end)
        + (if has("data") then { resource: { data: (.data | expand) } } else {} end)),
      functionMocks: [ (.mocks // [])[] | mock ]
    } + (if has("resource") then { resource: { data: (.resource | expand) } } else {} end) ] }
  }' "$cases")"

response="$(curl -sS -X POST \
  -H "Authorization: Bearer $token" \
  -H "x-goog-user-project: $project" \
  -H "Content-Type: application/json" \
  --data-binary @- \
  "https://firebaserules.googleapis.com/v1/projects/$project:test" <<<"$suite")"

if ! jq -e '.testResults' >/dev/null <<<"$response"; then
  echo "Rules Test API error:" >&2
  echo "$response" >&2
  exit 1
fi
results="$(jq -s '.[0] + .[1].testResults' <(printf '%s' "$results") <(printf '%s' "$response"))"
done

# One line per case; the API marks a result SUCCESS when the verdict matched the expectation.
jq -r --slurpfile cases "$cases" '
  [., $cases[0].cases] | transpose[]
  | "\(if .[0].state == "SUCCESS" then "PASS" else "FAIL" end)  \(.[1].expect | ascii_upcase)  [\(.[1].group)] \(.[1].name)"
    + (if .[0].state == "SUCCESS" or ((.[0].debugMessages // []) | length) == 0 then ""
       else "\n      " + (.[0].debugMessages | join("; ")) end)
' <<<"$results"

total="$(jq 'length' <<<"$results")"
failed="$(jq '[.[] | select(.state != "SUCCESS")] | length' <<<"$results")"
echo "$((total - failed))/$expected passed"
[ "$failed" -eq 0 ] && [ "$total" -eq "$expected" ]
