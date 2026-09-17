# Mgboard — Masaram Gondi Keyboard for Android

[![JVM tests](https://github.com/saiyyamdeveloper/Mgboard_App/actions/workflows/jvm-tests.yml/badge.svg)](https://github.com/saiyyamdeveloper/Mgboard_App/actions/workflows/jvm-tests.yml)

**|| जय सेवा ||**

🌿 **Mgboard** is a privacy-first, offline Android keyboard (IME) for the **Masaram Gondi
script** (मासाराम गोंडी लिपि, Unicode U+11D00–U+11D5F) — a full system keyboard with the
settings, grid menu, themes, toolbar and gesture-navigation behaviour of a modern
keyboard, plus on-device translation and rich-media panels.

Developed by **[Saiyyam Ji (@saiyyamdeveloper)](https://github.com/saiyyamdeveloper)**.

---

## ✨ Features

### Three keyboard modes (🌐 globe cycles through them)

| Mode | Layout |
|---|---|
| **Masaram Gondi** | Native 6-row layout — 10 independent vowels (`𑴀`–`𑴋`), 37 consonants/conjuncts (`𑴌`–`𑴰`), 10 matras |
| **हिंदी → Gondi** | Type in familiar Devanagari, get Masaram Gondi Unicode out — including nukta letters (`क़ ख़ ग़ ज़ ड़ ढ़ फ़ य़`) via long-press |
| **English QWERTY** | Auto-capitalization, single-tap shift (one-shot), double-tap caps-lock |

### Typing engine

- **Dynamic Row 1** — vowel row switches to matra row automatically after a consonant
- **युक्त (yukt) engine** — `C1 + युक्त + C2` composes conjuncts, Ra-kara (`𑴎𑵇`) and
  Repha (`𑵆𑴎`)
- **Text is the source of truth** — composition state is re-derived from the text around
  the caret, so cursor moves, pastes and external edits never corrupt state
- **Backspace** — 400 ms initial repeat, then 70 ms (held)
- **Undo / Redo** — 300-step stack with word boundaries
- **Spacebar trackpad** — drag to move the cursor
- **Code-point safe everywhere** — Gondi lives in a supplementary Unicode plane, so all
  text handling goes through a code-point API, never `String.length`

### Keyboard chrome

- **Monochrome Gboard-style icons everywhere** — the whole UI (toolbar access points,
  features/edit menus, keyboard keys, settings pages, voice pill, media & translate
  panels) draws plain vector icons from `ui/MgIcons.kt` (24×24 viewport, ~2dp stroke,
  round caps) tinted per theme: **white on dark themes, Gboard gray `#5F6368` on
  light** — exactly how Gboard tints its icon set. No colored emoji in chrome; emoji/
  sticker/GIF panel *content* stays colorful because that is content, not icons.
- **Keyboard toolbar (suggestion strip)** built on Gboard's own model —
  **access points** (17 of them), capacity **5 portrait / 6 landscape** (valid range
  3–8), semicolon order storage, and overflow into the **features menu**. Includes
  undo/redo chips that appear *"when user edits existing text"* (Gboard's own rule) and
  a one-time "Access all keyboard features here" education footer.
- **Translate panel (on-device)**: Gboard-style **source ⇄ target** pickers with
  auto-detect, powered by **ML Kit Translate** — `hi` ↔ `en` officially supported,
  ~30 MB model downloaded once (Wi-Fi by default), then fully offline with zero
  permissions and zero cloud calls ("On-device · text never leaves this device").
  Romanized Hinglish is honestly gated with a clear reason.
- **Panels from the toolbar**: emoji/expression panel (5 tabs — Emoji · GIF · Stickers ·
  Favorites · Recents — with search and 9 categories of emoji), a **bundled MgBoard
  sticker pack** (12 original stickers drawn with the bundled Gondi font — offline,
  no API key), and **Klipy-powered GIFs · Clips · Stickers · Memes tabs** (free-for-life
  Tenor successor; paste the API key in-app and all tabs go live), symbols panel with Gboard's exact **8 categories**
  (`Numbers · Brackets · Arrows · Mathematics · List · Shapes · Emoticons · Recent`,
  Numbers showing native Gondi digits), clipboard panel with history, and an edit menu
  (select all / copy / cut / paste) driven by `InputConnection`. GIF/sticker insertion
  uses Android's **Commit Content API**, with Gboard's verbatim message when a text
  field doesn't opt in.
- **⊞ Grid menu — "More features"**: 21 tiles (20 grid + fixed mic), 6 per page,
  drag-to-customize, semicolon order storage
- **Honest gating (hide-nothing)**: features that need a backend or permission still
  appear, and show Gboard's own verbatim reason instead of silently vanishing
- **Themes**: system / light / dark / **AMOLED black**, Material You dynamic colour on
  Android 12+
- **One-handed mode** (left / right) and **keyboard height** (50 %–200 %) — both apply
  live, no keyboard restart
- **3-page symbols & calculator** with native Gondi digits (`𑵐`–`𑵙`)
- **🎙 Voice toolbar (Gboard-style pill)** — five states exactly like Gboard's
  "Assistant voice typing toolbar": full voice panel, horizontal pill, popup menu,
  symbols overlay and vertical pill. Drag it to a screen edge and it flips vertical
  (28 dp threshold + 8 dp hysteresis); position, orientation and mode all persist, so
  the pill is back the next time you open a text field — Gboard's own behaviour.
  Built as an **IME-window widget**: no `SYSTEM_ALERT_WINDOW`, no overlay permission,
  no foreground service (touch outside the pill passes through to the app via
  `onComputeInsets`). Dictation prefers on-device recognition and falls back to online.
  Transcript text goes through the *existing* `DevToGondi` pipeline — the converter is
  untouched, and the pill's ⌫ reuses the keyboard's own 400 ms / 70 ms repeat handler.
- **Gesture-navigation padding**: on gesture nav, the system's own inset becomes a gap
  painted in the keyboard background colour. Height always comes from `WindowInsets` —
  **no hardcoded pixel values** — and it re-applies live when you switch nav modes
- **Masaram Gondi font bundled in the APK** (system fonts don't cover this script —
  without it the glyphs render as tofu □)

### Settings

The complete settings tree ported 1:1 from
Gboard's own resources): **1055/1055 parity checks passing** — every page, item, label,
summary, default and search keyword.

---

## 📱 Install & enable

1. Build the APK (below) or install a release build
2. Open the **MgBoard** app
3. Tap **Open keyboard settings** → enable **MgBoard** in the system list
4. In any text field, tap 🌐 and choose **MgBoard**

> **No device handy?** The app has a built-in **Live preview** that runs the *real*
> engine and the *real* layout in-app — nukta composition, युक्त, backspace repeat,
> undo, grid menu, themes, one-handed mode and the height slider all work there without
> enabling the IME.

### Build

```bash
./gradlew assembleDebug     # APK
./gradlew test              # 314 pure-logic assertions
./gradlew installDebug      # install on a connected device
```

AGP 8.5.2 · Kotlin 2.0.20 · Compose BOM 2024.09.00 · compileSdk/targetSdk 35 · minSdk 24 · JDK 17.

Permissions: `VIBRATE` (haptics) and `RECORD_AUDIO` (voice typing). **No internet
permission is needed for typing.**

Or open the repo in Android Studio and press Run.

---

## 🧪 Tests

```bash
./scripts/run_jvm_tests.sh      # engine, converter, layouts, shift, grid menu, voice, toolbar, translate, media — 930 assertions
                                  # (GitHub Actions par bhi yahi suite har push par chalta hai — badge upar)
python3 scripts/check_parity.py # generated settings model vs research data — 1055/1055
```

The JVM suite runs **without the Android SDK** — the entire typing engine, layout model,
converter, shift machine, grid-menu logic, the whole voice-toolbar state machine and the
toolbar/suggestion-strip model are pure Kotlin behind four small seams (`TextInput`,
`SettingsSource`, `VoiceHost`, `EditorActions`), so they compile and run on a plain JVM.

See [`docs/BUILD-AND-TEST.md`](docs/BUILD-AND-TEST.md).

---

## 🏗 Architecture

```
MgBoardIme (InputMethodService)  ──▶  KeyboardModel  ──▶  TypingEngine ──▶ TextInput
        │                                   │                                  ▲
Compose KeyboardScreen              QwertyShift · GridMenu                 ImeTextInput
KeyGestures (400/70/300ms)          KeyboardLayout · KeySpec            (InputConnection)
GestureNavPaddingController         DevToGondi (voice/bulk converter)
MgTheme · MgondiFont (bundled TTF)  SgPrefs (stable storage keys)
```

Full diagram, module table and design rules: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)

### Generated code

Layout data, the settings model and the grid-menu tiles are **generated from research
app**, never hand-edited:

| Script | Output |
|---|---|
| `scripts/gen_keyboard_data.py` | `data/KeyboardData.kt` |
| `scripts/gen_settings_model.py` | `model/SettingsModel.kt` |
| `scripts/gen_grid_menu.py` | `grid/GridMenu.kt` |
| `scripts/check_parity.py` | parity report (non-zero exit on mismatch) |

Update the research data → re-run the generators → parity is re-verified automatically.

---

## 📚 Research

Every feature here was researched **before** it was implemented, from Gboard's own APK
(18.3.1-beta `resources.arsc` + internal flag keys), AOSP source and official docs.
The full research is in [`docs/research/`](docs/research/README.md):

| Project | Status |
|---|---|
| [padding-project](docs/research/padding-project/) — gesture-nav safe-area gap | ✅ implemented & wired |
| [grid-menu-project](docs/research/grid-menu-project/) — ⊞ "More features" | ✅ implemented |
| [mgboard-setting-project](docs/research/mgboard-setting-project/) — settings tree | ✅ implemented (1055/1055) |
| [toolbar-project](docs/research/toolbar-project/) — keyboard toolbar / suggestion strip | ✅ implemented (access points, capacity/overflow, panels) |
| [voice-pill-project](docs/research/voice-pill-project/) — voice typing pill | ✅ implemented (IME-window widget, 5 states, drag-flip, persistence) |

Parity details, including a cluster-boundary quirk that is deliberately **locked** rather than fixed:
[`docs/PARITY.md`](docs/PARITY.md)

---

## 🔒 Privacy

- No network permission is needed for typing — the keyboard is fully offline
- No analytics, no ads, no third-party SDKs
- Text never leaves the device

---

## 🙏 Credits & licensing

- **Masaram Gondi font**: [Noto Sans Masaram Gondi](https://github.com/notofonts/masaram-gondi),
  © 2022 The Noto Project Authors, **SIL Open Font License 1.1** —
  license text bundled at [`app/src/main/res/font/OFL-masaram-gondi.txt`](app/src/main/res/font/OFL-masaram-gondi.txt)
- **Research & parity docs**: [`docs/research/`](docs/research/) (Gboard APK evidence + parity rules)
- Settings labels, summaries and gate strings are quoted from **Gboard** for
  interoperability/parity research. This project is an independent community effort and
  is **not affiliated with, endorsed by, or connected to Google LLC**.
- Code in this repository: **MIT License** (see `LICENSE`)

---

## 🗺 Roadmap

- [x] Typing engine port (Gondi / Hindi→Gondi / QWERTY) + undo + converter
- [x] Settings tree with 1055-point parity
- [x] Grid menu (21 tiles), themes, one-handed, height, symbols
- [x] Keyboard toolbar: 17 access points, capacity/overflow rules, emoji + symbols +
      clipboard + edit-menu panels
- [x] Gesture-navigation padding (Gboard-exact, insets-based)
- [x] Masaram Gondi font bundled
- [ ] Real-device pass: composing-region behaviour across editors, foldables, landscape
- [ ] Personal dictionary + clipboard manager wiring
- [x] Voice toolbar (5 states, drag/dock/flip, persistence, on-device-preferred dictation)
- [ ] Voice **commands** ("delete line", "go to settings", rephrase) — menu item currently gated honestly
- [ ] Word/next-word **suggestions** in the strip (needs a prediction model — currently
      no chips, honestly gated)
- [ ] Writing Tools / Proofread backends (panels exist, gated with Gboard's verbatim
      reasons) — ML Kit GenAI proofreading is a candidate for Pixel-class devices
- [ ] Klipy production API key approval (testing key works at 100 req/hour) and written
      confirmation of the transient delivery-buffer architecture
