# Build & Test

Do tarah ke test hain: **pure-logic JVM tests** (bina Android SDK ke, yahin chal jaate
hain) aur **Gradle build** (Android Studio / local machine par).

---

## 1. Pure-logic JVM tests — `./scripts/run_jvm_tests.sh`

Engine, converter, layouts, grid-menu, QWERTY shift, timings aur **poori voice-toolbar
state machine** — sab plain JVM par compile + run hote hain. **930 assertions.**

```bash
./scripts/run_jvm_tests.sh          # kotlinc + java chahiye
KOTLINC=/path/to/kotlinc ./scripts/run_jvm_tests.sh
```

Runner kya karta hai:

1. `app/src/main/kotlin` + `app/src/test/kotlin` ki har `.kt` file scan karta hai
2. skip karta hai: (a) `import android.*` / `import androidx.*` wali files,
   (b) jinki pehli line `// android-only:` marker ho
3. baaki ko `kotlinc -include-runtime` se jar banata hai aur
   `com.mgboard.keyboard.AllTests` chalata hai

> Sandbox/CI mein Android SDK nahi hota, isliye yeh harness zaroori hai. Local machine
> par `./gradlew test` bhi chalega (wahi assertions, `src/test/kotlin` se).

Kya cover hota hai:

| Section | Assertions ka focus |
|---|---|
| `CpText` | code-point counting, surrogate pairs, join/split |
| `KeyboardData` | Gondi/Hindi/QWERTY/Numbers ke rows, long-press alternates, classes |
| `TypingEngine` | row1 vowel↔matra, consonant detection, युक्त lookahead/flush, backspace, undo/redo (300 limit), num pages |
| `DevToGondi` | nukta composition, conjuncts, REPHA/RAKARA, KSSA/TRA, candra vowels, HALANTA vs VIRAMA, digits |
| `KeyTiming` | 400/70/300ms, deadzone 14, space drag 6/16, shift window 350 |
| `KeyboardLayout` | rows ka shape, control row ke web flex ratios, period ke 16 alternates, QWERTY shift/backspace |
| `QwertyShift` | auto-cap rules, double-tap caps lock, manual lock override |
| `GridMenu` | 21 tiles, capacity 3–8 (5 portrait/6 landscape), semicolon order, Gboard gate reasons |
| `VoiceWidgetRules` | 28dp edge-flip + 8dp hysteresis, 150–250ms animations, Gboard ke `widget_x_position` key names, dp sizing |
| `WidgetDrag` | drag, edge-flip (direction-gated), flip-back hysteresis, dock, drag-lock, adaptive width cap (chhoti screen regression), re-clamp |
| `VoiceWidgetTransitions` | §10.3 ka poora transition table — mic/chevron/⌨/badge/menu/symbols/dismiss + animation durations |
| `VoiceWidgetController` | 5 states, mic pause-resume (pill band nahi hoti), auto-pause on menu, permission/unavailable gating, offline preference, insert-through-existing-pipeline, tooltip first-drag, persistence + restore, language list |
| `StatusText` / `VoiceMenuItem` | Gboard APK se verbatim EN + HI strings ("अब बोलें", "रोकी गई", "वर्टिकल टूलबार पर स्विच करें") |
| `AccessPoints` / `ToolbarPanel` | 17 access points ka inventory, verbatim labels + permission prompts, 11 panels ke `Open X`/`Close X` labels |
| `SuggestionStrip` | capacity 3–8 clamp (5/6 default), semicolon order, overflow → features menu, right-side fixed access points, 14 Gboard config flags, undo/redo chips ka "existing text" rule |
| `SymbolPanel*` | Gboard ki 8 categories (APK order), shapes/emoticons grids, expression panel ke 5 tabs + GIF/Stickers gating |
| `TranslateLang` / `TranslateController` | ML Kit supported languages (`hi`/`en` official), English-pivot rule, script detection (romanized-Hindi gate), swap/auto-detect, 300 ms debounce, stale-result discard, model download states |
| `BundledStickers` / `KlipyApi` / `MiniJson` | 12-sticker pack (search EN+HI tags), Klipy URL rules (per_page 8–50, locale, encoding), response parse (sm.webp preview / md.gif full), availability reasons (Gboard verbatim field-unsupported toast) |

---

## 2. Settings parity check — `./scripts/check_parity.py`

Generated `SettingsModel.kt` ko web ke `index.html` se compare karta hai:

```bash
python3 scripts/check_parity.py     # → 1055/1055 OK
```

Har page/item ka id, label, summary, type, default, search keywords aur count match
hota hai. Web mein settings badle to:

```bash
python3 scripts/gen_settings_model.py && python3 scripts/check_parity.py
```

---

## 3. Code generators

| Script | Input | Output |
|---|---|---|
| `scripts/gen_keyboard_data.py` | `../Mgboard_Web/index.html` (arg1 se override) | `app/src/main/kotlin/.../data/KeyboardData.kt` |
| `scripts/gen_settings_model.py` | same | `app/src/main/kotlin/.../model/SettingsModel.kt` |
| `scripts/gen_grid_menu.py` | same (`EXT_ITEMS`) | `app/src/main/kotlin/.../grid/GridMenu.kt` |
| `scripts/check_parity.py` | dono | report (exit code non-zero on mismatch) |

**Generated files ko haath se edit mat karo** — web badle to script chalao.

`gen_keyboard_data.py` supplementary code points (U+11D00+) ke liye
`String(intArrayOf(0x11D0C), 0, 1)` emit karta hai, kyunki Kotlin ka `\uXXXX` escape
sirf 4 hex digits leta hai (`"\u11D0C"` = U+11D4 + `'0'` + `'C'` → silent corruption).

---

## 4. Gradle build (Android Studio / local)

```bash
# Android Studio: repo kholo → Gradle sync → Run 'app'
./gradlew assembleDebug        # APK
./gradlew test                 # unit tests (wahi 314 assertions)
./gradlew installDebug         # device par install
```

Versions (ek saath tested): **AGP 8.5.2 · Kotlin 2.0.20 · Compose BOM 2024.09.00**
compileSdk/targetSdk 35, minSdk 24, JDK 17.

> Repo mein `gradle/wrapper/gradle-wrapper.properties` hai par `gradlew` script +
> `gradle-wrapper.jar` nahi (binary). Pehli baar Android Studio mein kholne par
> wrapper khud ban jaata hai, ya `gradle wrapper --gradle-version 8.7` chala lein.

---

## 5. Device par keyboard enable karna

1. APK install karein
2. App kholein → **"MgBoard keyboard chalu karein"** card → *Open keyboard settings*
3. List mein **MgBoard** ko ON karein
4. Kisi bhi text field mein → 🌐 (globe) → **MgBoard** chunein

Bina enable kiye bhi app ke andar **Live preview** chalta hai (asli engine, asli
layout) — nukta composition, युक्त, backspace repeat, undo, grid menu, themes,
one-handed, height slider sab wahi test ho jaata hai.

---

## 6. Font (Masaram Gondi)

Gondi script U+11D00–U+11D5F mein hai aur Android ke system fonts mein yeh **nahi**
hoti. Isliye font APK mein bundle hai:

```
app/src/main/res/font/noto_sans_masaram_gondi.ttf   (Noto Sans Masaram Gondi, OFL 1.1)
app/src/main/res/font/OFL-masaram-gondi.txt         (license — redistribution ke liye zaroori)
app/src/main/res/font/mgondi.xml                    (font-family, Compose/XML dono ke liye)
```

Verified: U+11D00, U+11D0C, U+11D42 (NUKTA), U+11D44 (HALANTA), U+11D45 (VIRAMA),
U+11D46 (REPHA), U+11D47 (RAKARA), U+11D50–U+11D59 (digits) — sab glyphs maujood hain.
