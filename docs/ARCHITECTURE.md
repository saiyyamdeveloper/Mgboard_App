# MgBoard — Architecture

Ek hi typing engine, do shells (web demo + Android IME). Android app ka poora logic
[`Mgboard_Web`](https://github.com/saiyyamdeveloper/Mgboard_Web) ke `index.html` se
**1:1 port** kiya gaya hai — behaviour badla nahi gaya, sirf language badli hai.

```
┌─────────────────────────────────────────────────────────────────────┐
│  Android shell (Android-only)                                       │
│  MgBoardIme (InputMethodService) ── Compose KeyboardScreen          │
│         │                              │                            │
│         │  ImeTextInput                │  KeyGestures               │
│         │  (InputConnection ↔          │  (400ms/70ms repeat,       │
│         │   TextInput adapter)         │   300ms long-press)        │
│         │                              │                            │
│         │  GestureNavPaddingController │  MgTheme (4 themes)        │
│         │  (padding-project)           │  MgondiFont (bundled TTF)  │
└─────────┼──────────────────────────────┼────────────────────────────┘
          ▼                              ▼
┌─────────────────────────────────────────────────────────────────────┐
│  Pure Kotlin (koi Android import nahi → plain JVM par test hota hai) │
│                                                                     │
│  KeyboardModel ── TypingEngine ── UndoStack                         │
│  VoiceWidgetController ── WidgetDrag ── VoiceWidgetTransitions      │
│  SuggestionStrip ── AccessPoints ── ToolbarFlags ── ToolbarPanel     │
│  TranslateController ── TranslateEngine(ML Kit) ── KlipyApi ── commit │
│       │               │                                             │
│       │               ├── TextInput (interface)                     │
│       │               └── DevToGondi (voice/bulk converter)         │
│       ├── QwertyShift (auto-cap + caps lock)                        │
│       ├── KeyboardLayout + KeySpec (rows, web ke flex ratios)       │
│       ├── GridMenu + GridIcons (21 tiles, capacity 3–8)             │
│       ├── data/KeyboardData (Gondi/Hindi/QWERTY/Numbers, generated) │
│       ├── model/SettingsModel (Gboard parity, generated)            │
│       └── prefs/SgPrefs + SgStore (web ke localStorage keys)        │
└─────────────────────────────────────────────────────────────────────┘
```

## Module-wise breakdown

| Package | Lines | Kaam | Source of truth |
|---|---|---|---|
| `engine/` | 718 | composition state, युक्त lookahead, backspace, undo, sync-from-text | web `state` + `insertCharacter`/`backspace`/`onYukt`/`syncImeContext` |
| `data/` | 523 | har layout ka key data (glyph, label, long-press alternates, classes) | **generated** from web by `scripts/gen_keyboard_data.py` |
| `layout/` | 287 | `KeySpec` model + rows + QWERTY shift machine + timings | web `buildLettersPanel`/`buildHindiPanel`/`buildQwertyPanel`/`attachKey` |
| `ime/` | 646 | `InputMethodService`, `InputConnection` adapter, platform-agnostic model | new (Android-specific) |
| `grid/` | 165 | grid-menu tile data + capacity/order rules | **generated** from web `EXT_ITEMS` by `scripts/gen_grid_menu.py` |
| `gridmenu/` | 976 | Gboard-exact grid-menu controller/customizer/panel-host | `docs/research/grid-menu-project/` |
| `model/` | 1102 | Gboard Settings ka poora tree (labels, summaries, search keywords) | **generated** by `scripts/gen_settings_model.py`, verified by `check_parity.py` |
| `prefs/` | 206 | SharedPreferences wrapper — keys web ke localStorage keys ke same | web `sg*` functions |
| `translate/` | ~330 | On-device translate panel ka model — language inventory, phases, script detection, controller (debounce/stale-discard/model states); ML Kit `TranslateEngine` seam ke peeche | `docs/research/toolbar-project/TRANSLATE-GIF-FEASIBILITY.md` |
| `media/` | ~480 | GIF/Stickers layer — bundled pack data (generated), Klipy API client + MiniJson parser, availability gating; Android par `MediaCommitController` (Commit Content API + FileProvider) | same |
| `toolbar/` | ~330 | Gboard keyboard toolbar model — access points, flags, suggestion-strip build (capacity/order/overflow/chips), panel enums, symbols categories | `docs/research/toolbar-project/` |
| `voice/` | ~1200 | Gboard-style voice toolbar — 5 states, drag/flip/dock, persistence, dictation bridge, Compose UI | `docs/research/voice-pill-project/` |
| `ui/` | 1173 | Compose keyboard, themes, popups, settings screen, in-app preview; **`MgIcons.kt`** = monochrome Canvas vector icon set (~40 ids, 24×24 viewport, ~2dp stroke) + `mgIconTint()` (white on dark themes, `#5F6368` on light — Gboard tint parity). Saara UI chrome isi se render hota hai; colored emoji sirf panel content (emoji/stickers/GIFs) mein | web CSS/JS render + `mgboard-setting-project` |

## Design rules (jo kabhi nahi todne)

1. **Behaviour web ke barabar rahe.** Converter, nukta composition, backspace timing
   (400ms initial / 70ms repeat), fonts, layout — inmein koi change nahi. Naya feature
   *additive* ho.
2. **Text hi source of truth hai.** Composition state kabhi "last pressed key" se infer
   nahi hota — `syncImeContext()` caret ke aas-paas ke text se state banata hai. Isliye
   cursor move, paste, external edit sab sahi handle hote hain.
3. **Hide-nothing gating.** Jo feature abhi kaam nahi karta woh chhupaya nahi jaata —
   tile dikhta hai aur Gboard ka verbatim reason deta hai (jaise
   `"Command not available in this app"`).
4. **Generated code ko haath se edit mat karo.** `data/`, `model/`, `grid/` scripts se
   bante hain. Web mein layout badle to script dobara chalao.
5. **Code point safe.** Gondi U+11D00+ (supplementary plane) mein hai — har jagah
   `CpText` use hota hai, `String.length`/`charAt` nahi. Kotlin mein supplementary
   code point `String(intArrayOf(0x11D0C), 0, 1)` se likha jaata hai
   (`"\u11D0C"` galat parse hota hai).
6. **Padding system insets se.** Gesture-navigation gap ke liye koi fixed px value
   nahi — `WindowInsets` (details: `docs/research/padding-project/`).

## Toolbar Gboard ke apne model par bana hai

`toolbar/` package Gboard ki vocabulary use karta hai: **access points** (icons),
**suggestion strip** (toolbar), **features menu** (overflow). Capacity/order/overflow ke
rules grid-menu-project ke shared hain (Gboard ka ek hi rule dono jagah): 5 portrait /
6 landscape, range 3–8, semicolon order, jo fit na ho → features menu.

Flag OFF hone par access point strip se hat kar **features menu mein chala jaata hai** —
feature chhupta nahi (hide-nothing).

## Voice toolbar ek additive module hai

`voice/` package keyboard ke key-event pipeline ko **chhuta nahi**. Do seam use hote hain:

- text insert → `KeyboardModel.insertBulk()` (jo Gondi mode mein `DevToGondi.convert()` chalata hai)
- backspace → `TypingEngine.backspace()` + UI ka wahi `KeyGestures` repeat handler (400ms/70ms)

Window strategy **(A) IME-window widget** hai (Gboard-exact): pill wahi IME window hai
collapsed state mein, `onComputeInsets()` ke `TOUCHABLE_INSETS_REGION` se baaki screen
par tap pass-through hota hai. Isliye na overlay permission chahiye, na foreground
service — mic "while-in-use" chalta hai.

## Ek hi Compose view, do jagah

`KeyboardScreen(model, host)` ko do host drive karte hain:

- `MgBoardIme` — real IME (`host.syncFromEditor()` `InputConnection` se text kheenchta hai)
- `PreviewHost` — launcher ke andar live preview (in-memory `PreviewTextInput`)

Isse preview aur real keyboard ka behaviour drift nahi kar sakta.

## Testable seams

| Seam | Kyun |
|---|---|
| `TextInput` interface | engine ko editor se decouple karta hai → JVM tests + preview |
| `KeyboardModel.SettingsSource` | prefs/Android ko model se alag → JVM tests + preview |
| `QwertyShift.onShiftTap(now)` | clock inject → double-tap window testable |
| `VoiceHost` interface | voice toolbar ko Android (mic/IME/prefs) se alag karta hai → poori state machine JVM par test hoti hai |
| `EditorActions` interface | edit-menu/IME-action ko `InputConnection` se alag karta hai → toolbar model JVM par test hota hai |
| `TranslateEngine` interface | ML Kit ko model se alag karta hai → poora translate state machine JVM par test hota hai; preview fake engine use karta hai |
| `MediaProvider`/transport lambda | Klipy network call inject hota hai (`httpGet`, `klipyFetch`) → URL/parse logic JVM par test hota hai |
| `// android-only:` marker | runner ko batata hai kaun si file JVM par compile nahi ho sakti |
