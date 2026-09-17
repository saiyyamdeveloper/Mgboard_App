#!/usr/bin/env bash
# JVM unit tests — bina Gradle/Android SDK ke (kotlinc + java).
#
# Sandbox/CI mein Android SDK nahi hota, isliye pure-logic code (engine, data,
# converter, prefs) ko alag compile karke chalaya jaata hai. Android-framework
# wale classes (Activity/Service/Compose) isme compile nahi hote — unke liye
# `./gradlew test` local machine par chalayein.
#
# Requirements: kotlinc (KOTLINC env ya PATH par) + java 11+
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
KOTLINC="${KOTLINC:-kotlinc}"
command -v "$KOTLINC" >/dev/null 2>&1 || KOTLINC="$(ls -d /tmp/kotlinc/bin/kotlinc 2>/dev/null || true)"
[ -n "$KOTLINC" ] || { echo "ERROR: kotlinc not found (set KOTLINC=/path/to/kotlinc)"; exit 2; }

OUT="${TMPDIR:-/tmp}/mgboard-jvm-tests"
rm -rf "$OUT"; mkdir -p "$OUT"

# Sirf pure-Kotlin sources. Skip rules:
#   1) file Android/AndroidX import karta ho, ya
#   2) pehli line `// android-only:` marker ho — un files ke liye jo pure Kotlin
#      hain par Android-only types (e.g. KeyboardModel, Compose state) use karti hain.
SRC=()
while IFS= read -r f; do
  if grep -qE '^import (android\.|androidx\.)' "$f"; then continue; fi
  if head -1 "$f" | grep -q '^// android-only:'; then continue; fi
  SRC+=("$f")
done < <(find "$ROOT/app/src/main/kotlin" "$ROOT/app/src/test/kotlin" -name '*.kt' | sort)

echo "compiling ${#SRC[@]} pure-Kotlin files…"
"$KOTLINC" "${SRC[@]}" -include-runtime -d "$OUT/tests.jar" 2>&1 | grep -v '^warning:' || true
[ -f "$OUT/tests.jar" ] || { echo "ERROR: compile failed"; exit 1; }
java -Dfile.encoding=UTF-8 -cp "$OUT/tests.jar" com.mgboard.keyboard.AllTests
