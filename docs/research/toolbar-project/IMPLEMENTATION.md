# toolbar-project → Mgboard implementation

**Status: ✅ implemented** (`app/src/main/kotlin/com/mgboard/keyboard/toolbar/` + `ui/KeyboardToolbar.kt` + `ui/ToolbarPanels.kt`)

`RESEARCH.md` (Gboard 18.3.1-beta APK evidence) ke rules ab code mein hain. Yeh file
batati hai kya bana, kahan bana, aur kya honestly gated hai.

---

## 1. Gboard ki vocabulary use ki gayi (§10.1)

| Research ka naam | Code mein |
|---|---|
| keyboard toolbar / suggestion strip | `ui/KeyboardToolbar.kt` → `KeyboardToolbar()` |
| access points | `toolbar/ToolbarModel.kt` → `AccessPoint`, `AccessPoints` |
| features menu (overflow) | `AccessPoints.FEATURES_MENU` + `FeaturesMenuPanel` |
| panels | `ToolbarPanel` enum + `ToolbarPanelHost` |
| expression footer / navbar | `EmojiPanel` ke tabs + category nav |
| more keyboard options | `MoreKeyboardOptionsPanel` |

User-facing strings Gboard ke hi hain ("More features" / "ज़्यादा सुविधाएं",
"Access all keyboard features here" / "कीबोर्ड की सभी सुविधा यहां पाएं").

---

## 2. Files

| File | Kaam | Pure Kotlin? |
|---|---|---|
| `toolbar/ToolbarModel.kt` | `ToolbarPanel` (11, open/close labels), `AccessPoint` + `AccessPoints` (17), `ToolbarAction`, `ToolbarFlags` (14 Gboard config keys), `SuggestionStrip.build()` (capacity/order/overflow/chips), `SymbolPanelCategory` (8) + `SymbolPanelData` | ✅ |
| `toolbar/ToolbarItem.kt` | menu row item (label/summary/glyph/action) | ✅ |
| `ui/KeyboardToolbar.kt` | strip render + access-point items + undo/redo chips + education footer + panel host + features menu + more-options + edit menu | ❌ Compose |
| `ui/ToolbarPanels.kt` | Emoji/expression panel (5 tabs, search, category nav, grid), Symbols panel (8 categories), Clipboard panel | ❌ Compose |
| `ime/KeyboardModel.kt` | `strip()`, `featuresMenu()`, `onAccessPoint()`, `symbolsPanelGrid()`, `moreKeyboardOptions()`, `editMenuItems()`, clipboard/recents | ✅ (Compose state ke alawa) |
| `ime/PrefsSettingsSource.kt` | Gboard ke APK config-key names par SharedPreferences, clipboard history, editor actions | ❌ Android |
| `ime/MgBoardIme.kt` | `EditorActions` (select all / copy / cut / paste / IME action) + system IME picker | ❌ Android |
| `prefs/SgStore.kt` | clipboard history `[{text, ts}]` (web ke `cpLoad/cpSave` jaisa) | ❌ Android |

Tests: `app/src/test/kotlin/com/mgboard/keyboard/ToolbarTests.kt` — **146 assertions**.

---

## 3. Gboard-exact jo implement hua

### Capacity / order / overflow (§2)
- default **5 portrait / 6 landscape**, valid range **3–8** (`GridMenu.capacity` ke shared rules)
- order **semicolon-separated**, storage key `access_points_showing_order` (Gboard ka apna naam)
- **overflow** → features menu: capacity se bahar ke access points ⊞ ke andar chale jaate hain
- falsy-zero guard: stored `0` ya `""` → orientation default (web wala parseInt trap dobara nahi)

### Access points (§3 inventory)
17 access points: voice, emoji, clipboard, translate, writingTools, proofread,
quickInsert, symbols, theme, oneHanded, settings, undo, redo, imeAction, imeSwitch,
features menu (⊞, **fixed**), more keyboard options (⋮).

- Gboard-exact labels (APK permission strings se proof):
  *"Allow Voice Typing to access your microphone and voice recordings?"*,
  *"Allow Quick Insert to access your contacts?"*, *"Access clipboard"*, *"Access translate"*
- **⊞ fixed** hai — pin se hataaya ya drag nahi kiya ja sakta (§0.2)
- right-side access points: IME action (↵), IME switch (🌐), features menu (⊞)

### Flags (§2 ke APK config keys — naam ke saath)
`show_toolbar`, `enable_ime_action_access_point`, `enable_ime_switch_access_point`,
`enable_undo_redo_via_access_point`, `enable_writing_tools_icon_in_suggestion_strip`,
`enable_show_emoji_key_in_horizontal_toolbar`, `enable_access_point_customization`,
`enable_access_point_education_footer`, `enable_auto_hide_keyboard_header`,
`enable_suggestion_strip_popup_menu`, `enable_clipboard_content_suggestion`,
`enable_strip_trailing_space_divider`, `force_enable_horizontal_toolbar_on_foldables`,
`enable_quick_insert`

**Flag OFF → access point strip se hat kar features menu mein chala jaata hai** (feature
chhupta nahi — hide-nothing). Research ka string yahi kehta hai: *"Show a button on the
keyboard toolbar to open emoji keyboard"* — button strip se jaata hai, feature rehta hai.

### Undo/Redo chips (§2)
APK verbatim: *"The Undo and Redo chips appear in the suggestion strip via access point,
**when user edits existing text**."* → chips sirf tab dikhte hain jab editor mein text ho
aur undo/redo stack mein kuch ho.

### Panels (§5)
| Panel | Status |
|---|---|
| **Emoji / expression** | ✅ 5 tabs (`Emoji · GIF · Stickers · Favorites · Recents`), search box, category side-nav (9 categories, 297 emoji — web ke `EmojiData` se), grid. GIF/Stickers gated |
| **Symbols** | ✅ Gboard ki **8 categories** exact: `Numbers · Brackets · Arrows · Mathematics · List · Shapes · Emoticons · Recent`. Numbers mein **Gondi digits** (U+11D50–U+11D59) |
| **Clipboard** | ✅ history (`[{text, ts}]`), tap = paste, ✕ = delete, "Clear all" |
| **Edit menu** | ✅ Select all / Copy / Cut / Paste — sab `InputConnection` se, copy-cut par clip history mein bhi jaata hai |
| **More keyboard options** | ✅ one-handed, theme, height, toolbar toggle, edit menu, select mode, settings |
| **Features menu (⊞)** | ✅ overflow access points + grid-menu ke 20 tiles |
| Translate / Writing Tools / Proofread / Select mode | 🚧 **honest gating** — panel khulta hai aur Gboard ka verbatim reason dikhata hai |

Har panel ka header + footer Gboard ke pattern se: *"Open X"* / *"Close X"* /
*"Close the emoji panel"* / *"सिंबल वाला पैनल बंद करें"* (TalkBack ke liye zaroori — §10.5).

---

## 4. Jo honestly gated hai (hide-nothing)

| Cheez | Kyun | UI mein kya dikhta hai |
|---|---|---|
| **Word/next-word suggestions** | prediction model chahiye (IME ka sabse bada hissa); MgBoard ke paas abhi koi language model nahi | strip par suggestion chips nahi aate; access points + undo/redo chips chalte hain. Settings ka "Show suggestions" row gated reason deta hai |
| Translate panel | translation backend chahiye | *"Can't use this tool at the moment. Please try again later."* |
| Writing Tools / Proofread | Pixel/Assistant-specific + model | *"Can't proofread text in this field"* |
| Quick Insert | contacts opt-in flow | *"Disabled because opt-in is disabled"* |
| GIF / Stickers tabs | content provider chahiye | *"Command not available in this app"* |
| Emoji Kitchen | backend | access point grid menu mein gated tile |

---

## 5. Jo research mein tha, MgBoard par **laagu nahi hota**

| Item | Kyun nahi |
|---|---|
| **PK toolbar** (physical/Bluetooth keyboard) | MgBoard ka PK mode hi nahi hai |
| **Stylus toolbar** (S Pen handwriting) | handwriting support nahi hai |
| `voice_toolbar_*` keys | yeh voice-pill-project ka hissa hain — wahan implement hue |
| Foldable-specific keys | `force_enable_horizontal_toolbar_on_foldables` flag model mein hai (default OFF), par foldable detection device par verify karna baaki hai |

---

## 6. Standing rules jo follow hue

| Rule | Kaise |
|---|---|
| **Additive** | purana `ToolbarStrip` composable hata kar `KeyboardToolbar` aaya, par keyboard body / key pipeline / converter / engine ko chhua nahi. Voice pill ke saath bhi koi conflict nahi (`model.voiceRequest` se wire hota hai) |
| **Backspace 400/70ms reuse** | panels ke ⌫ wahi `TypingEngine.backspace()` call karte hain; keyboard ke ⌫ ka repeat handler jaisa hai waisa hi hai |
| **Fonts unchanged** | strip/panels ke text par wahi bundled `MgondiFont` (Gondi digits/symbols sahi dikhein) |
| **dp-based sizing** | strip 42dp, access point 38dp, panels 200–230dp — koi fixed px nahi |
| **hide-nothing** | gated access points ⊘ glyph ke saath dikhte hain, tap par verbatim reason |
| **Gboard-exact strings** | `strings.xml` mein 39 naye EN + 22 HI strings, APK se verbatim |

---

## 7. Tests

`ToolbarTests.kt` — 146 assertions:

- Gboard vocabulary + default pinned set (§10.3 minimum viable + symbols)
- 17 access points ka inventory, verbatim EN/HI labels, permission prompts, gated reasons
- 11 panels ke `Open X` / `Close X` labels (EN + HI)
- capacity 3–8 clamp, portrait 5 / landscape 6, custom semicolon order, unknown-id drop,
  dedupe, capacity limit, `toOrderSemicolon` round-trip
- overflow → features menu (grid tiles ke saath merge, no duplicates)
- right-side fixed access points + IME action/switch flags
- 14 Gboard config flags ke defaults + flag-OFF → features menu mein shift
- undo/redo chips: *"when user edits existing text"* ke exact conditions
- symbols panel ki 8 categories (APK order) + shapes/emoticons grids
- expression panel ke 5 tabs + GIF/Stickers gating + `EmojiData` (9 categories)

```
./scripts/run_jvm_tests.sh   → 679/679 PASS
```

---

## 8. Device par manually verify karna hai

- [ ] `LazyVerticalGrid` keyboard-height ke andar scroll/measure sahi ho
- [ ] Clipboard panel: Android 10+ par background clipboard read restriction
      (IME visible hone par hi `ClipboardManager` padha ja sakta hai — humara case OK hona chahiye)
- [ ] Copy/cut ka `getExtractedText` bade documents par performance
- [ ] `performEditorAction` har editor mein (Chrome, WhatsApp, Notes)
- [ ] TalkBack: access-point labels + panel open/close announcements
- [ ] Landscape/foldable par capacity 6 aur strip scroll

---

## 9. Translate panel + GIF/Stickers (research round 2, 2026-09-17)

User ke do sawaalon ka jawab web research se aaya (`TRANSLATE-GIF-FEASIBILITY.md`),
phir user-confirmed decisions par implement hua:

| Decision (user-confirmed) | Kya chuna |
|---|---|
| Translate engine | **ML Kit on-device** (`com.google.mlkit:translate:17.0.3`) — primary, koi fallback nahi |
| GIF/Stickers | **Bundled sticker pack + Klipy search** (placeholder key mode) |
| Klipy key | **Placeholder + setup screen** — key `local.properties`/env `KLIPY_APP_KEY` se, repo mein commit nahi |
| Order | Translate pehle |

### 9.1 Translate — kya bana

- `translate/TranslateModel.kt` (**pure Kotlin**): 9 languages (ML Kit list se; `hi`+`en`
  official), phases, script detection (romanized-Hindi honest gate), `TranslateController`
  (300 ms debounce, stale-result discard, swap, auto-detect, model download states)
- `ime/MlKitTranslateEngine.kt`: ML Kit implementation — per-pair `Translator` cache,
  Wi-Fi-only download default, `Translator.close()` lifecycle
- `ui/TranslatePanel.kt`: Gboard layout — `[source ▾] ⇄ [target ▾]`, output area,
  ✓ insert, "On-device · text never leaves this device" badge, "via English" pivot note
- Keyboard routing: translate mode mein typed text **editor mein nahi, translate buffer
  mein** jaata hai (Gboard jaisa); ✓ par `insertTextBulk` se editor mein
- Gating: engine unavailable / download fail / romanized Hindi — sab verbatim reasons

### 9.2 GIF/Stickers — kya bana

- `media/BundledStickers.kt` (generated: `scripts/gen_stickers.py` ← `res/raw/stickers_manifest.json`):
  **12 original stickers**, bundled Gondi font se draw (`scripts/stickers` generator nahi —
  PIL script se banaye, ~50 KB total), EN+HI tags se search, offline
- `media/MediaModel.kt` (**pure Kotlin**): `MediaItem/Page/Availability`, `KlipyApi`
  (URL rules: per_page 8–50, locale, percent-encoding; response parse), `MiniJson`
  (dependency-free parser)
- `media/MediaCommitController.kt`: **Commit Content API** pipeline — editor MIME opt-in
  check, FileProvider (`${applicationId}.mediaprovider`, `res/xml/media_paths.xml`),
  transient delivery buffer (`cacheDir/media/`, prune 12), `INPUT_CONTENT_GRANT_READ_URI_PERMISSION`
- `ui/MediaPanels.kt`: Stickers grid (Coil, raw res) + GIF tab (Klipy trending/search,
  explicit submit — testing key 100 req/hr; animated GIF decode `coil-gif`;
  "GIFs · KLIPY" attribution; key khali ho to setup hint)
- Gating matrix: editor support nahi → Gboard verbatim
  *"The text field does not support GIF insertion from the keyboard"*; key nahi → setup
  hint; offline → Gboard verbatim unavailable message. Bundled pack hamesha ready.

### 9.3 Naye dependencies

| Dependency | Kaam | Size impact |
|---|---|---|
| `com.google.mlkit:translate:17.0.3` | on-device translation | APK ~2–3 MB + models 30 MB/language (download) |
| `io.coil-kt:coil-compose:2.7.0` + `coil-gif` | animated GIF render in grid | ~1 MB |

Koi naya **permission** nahi juda (INTERNET pehle se tha — ML Kit model + Klipy ke liye).

### 9.4 Tests

`TranslateTests.kt` (87) + `MediaTests.kt` (87) — total suite **904/904 PASS**:
translate state machine, debounce/stale-discard, model download fail paths, script
detection, language inventory; sticker pack data + search, Klipy URL/parse rules,
MiniJson, availability reasons.
