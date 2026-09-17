# Parity Notes — Web ↔ Android ↔ Gboard

MgBoard ki teen parity layers hain. Yeh document batata hai ki **kya exactly match
kiya gaya hai**, aur jahan web mein koi quirk hai wahan **quirk ko jaan-boojh kar
lock kiya gaya hai** (fix nahi kiya).

Rule: converter, nukta composition, backspace timing, fonts, layout — inmein koi
behaviour change nahi. Feature sirf additive ho.

---

## 1. Android ↔ Web (typing engine) — 314 JVM assertions

### Layouts (web ke exact values)

| Cheez | Value | Test |
|---|---|---|
| Gondi/Hindi rows | row1 dynamic (10 vowel ↔ 10 matra) + rows 2–5 (10 keys each) + control row | `KeyboardLayout` |
| ROW5 slot 6 | `null` → dynamic vocalic-R key (combo mein consonant + VS_R) | ✔ |
| ROW5 slot 8 | `युक्त` (glyph = VIRAMA U+11D45, class `special`) | ✔ |
| ROW5 slot 9 | DEL → BACKSPACE (`danger`) | ✔ |
| Control row flex | `1.57 / 1.0 / 1.0 / 4.4 / 1.0 / 1.58` | ✔ |
| Space labels | Gondi `𑴎𑴽𑵀𑴘𑴳` · English · हिंदी | ✔ |
| Period long-press | **16** alternates `& % + " - : ' @ ; / ( ) # ! , ?` | ✔ |
| Toggle glyph | Gondi `?𑵑𑵒𑵓` (D1 D2 D3), baaki `?123` | ✔ |
| QWERTY | 3 rows (10/9/7+shift+backspace), shift & backspace weight 1.6 | ✔ |
| Numbers | 5 rows × 10, page 3 par CALC row add | ✔ |

### Timings (user constraint: inhe badalna nahi)

| Constant | Value | Web |
|---|---|---|
| `REPEAT_INITIAL_DELAY_MS` | 400 | `attachKey` hold timer |
| `REPEAT_INTERVAL_MS` | 70 | `repeatTimer` |
| `LONG_PRESS_MS` | 300 | long-press alternate |
| `MULTI_POP_DEADZONE_DP` | 14 | `DEADZONE_PX` |
| `SPACE_DRAG_THRESHOLD_DP` | 6 | `SPACE_DRAG_THRESHOLD` |
| `SPACE_DP_PER_CHAR` | 16 | `SPACE_PX_PER_CHAR` |
| `SHIFT_DOUBLE_TAP_MS` | 350 | caps-lock double tap |

### Code points

`HALANTA U+11D44` · `VIRAMA U+11D45` · `REPHA U+11D46` · `RAKARA U+11D47` ·
`NUKTA U+11D42` · digits `U+11D50–U+11D59` · 37 consonants · 10 vowel signs + HALANTA.

---

## 2. Verified converter behaviour (`DevToGondi`)

Tests mein Devanagari **hamesha `\uXXXX` escapes** se likha jaata hai — typed strings
mein virama ki position ghalat ho sakti hai (ek baar exactly yahi hua: typed `सर्व` =
स र ् व, jabki असली स्र्व = स ् र व).

| Input | Output | Note |
|---|---|---|
| `कमल` | 𑴌𑴤𑴧 | input mein virama nahi → conjunct nahi banta |
| `क्म` (क ् र ् व ्) | 𑴫𑵅𑴦𑵅𑴨𑵄 | 3-consonant cluster + word-final HALANTA |
| `स्र्व` (स ् र ् व) | 𑴫𑵅𑴦𑵅𑴨 | 3-consonant cluster |
| `र्द` | 𑵆𑴝 | REPHA prefix, virama nahi |
| `क्र` | 𑴌𑵇 | RAKARA, virama nahi |
| `क्रम` | 𑴌𑵇𑴤 | **web quirk** (neeche dekho) |
| `कर्म` (क ् र ् म) | 𑴌𑵅𑴦𑵅𑴤 | poora 3-consonant cluster |
| `क्ष` | 𑴮 | KSSA |
| `क्षय` | 𑴮𑴥 | **web quirk**: KSSA + YA, beech mein virama nahi |
| `त्र` | 𑴳 | TRA |
| `न्त्र` | 𑴟𑵅𑴳 | NA + VIRAMA + TRA |
| `क़/ज़/ड़/फ़/ऱ/ऩ/ऴ` | base + NUKTA | NFD precomposed letters ko decompose karta hai |
| standalone `़` | U+093C + NUKTA | **web-faithful** (drop nahi hota) |
| precomposed `ऑ` (U+0911) | O + CANDRA | |
| decomposed `ऑ` (O + U+0945) | passthrough | |
| precomposed `ॲ` (U+0972) | A + CANDRA | |
| word-final lone `्` | HALANTA (U+11D44) | conjunct-ke-andar wala `्` → VIRAMA (U+11D45) |
| Latin / space | passthrough | |
| Devanagari + Latin digits | Gondi digits | |

### 🔒 Web cluster-boundary quirk — **jaan-boojh kar lock, fix nahi**

JS mein `tokens[i+2]` array ke bahar `undefined` deta hai, aur `isCons(undefined)`
`false` hota hai. Kotlin port isi ko `i + 2 < tokens.size` se match karta hai.

Natija: 4-token `क ् र म` mein cluster sirf `[क, र]` banta hai → `KA + RAKARA + MA`
(poora `क ् र ् म` nahi). Isi tarah `क्षय` → `KSSA + YA`.

**Yeh behaviour dono apps mein same rehna chahiye.** `DevToGondi.kt` mein comment hai —
agar kabhi "fix" karna ho to web aur Android **ek saath** badlo, warna parity tootegi.

---

## 3. Android ↔ Gboard

### Settings — 1055/1055 parity

`scripts/check_parity.py` web ke `index.html` (jo khud Gboard 18.3.1-beta ke
`resources.arsc` se port hua) se generated `SettingsModel.kt` ko compare karta hai.
Har page/item ka id, label, summary, type, default, search keywords aur count match
karta hai. Research: `docs/research/mgboard-setting-project/`.

### Grid menu — Gboard-exact rules

| Rule | Value | Source |
|---|---|---|
| Total tiles | 21 (20 grid + fixed mic) | `EXT_ITEMS` |
| Capacity range | 3–8 inclusive | Gboard `grid_capacity_help` |
| Default | 5 portrait / 6 landscape | Gboard §0.1 |
| Grid icon | **fixed** (removable nahi) | Gboard §0.2 |
| Per page | 6 | `EXT_PER_PAGE` |
| Order storage | semicolon-separated ids | `access_points_showing_order` |
| Storage keys | `mg_access_points_count_on_bar`, `mg_pinned_ext_ids` | web ke localStorage keys |
| Gated tiles | 8 — Gboard ka **verbatim** reason dikhate hain | hide-nothing |

Verbatim gate reasons (tests mein assert):

- Translate / Writing Tools → `Can't use this tool at the moment. Please try again later.`
- Proofread → `Can't proofread text in this field`
- GIF / Sticker / Emoji Kitchen → `Command not available in this app`
- Quick Insert → `Disabled because opt-in is disabled`
- Scan text → `Gboard needs access to the camera in order to enable Scan text.`

Research: `docs/research/grid-menu-project/`.

### Gesture-navigation padding — Gboard-exact

Gboard APK (18.3.1-beta) ki internal keys se verify kiya gaya:

```
normal_mode_keyboard_bottom_gap_portrait / _landscape
normal_mode_decor_view_stable_inset_bottom_portrait / _landscape
```

Teen baatein naam se hi pakki hoti hain: (a) gap sirf **normal mode** mein
(floating/split/one-handed mein nahi), (b) height **DecorView ke stable bottom
inset** se aati hai, (c) portrait/landscape ke alag values hain.

Implementation: `ime/insets/GestureNavPaddingController.kt`

- height hamesha `getInsetsIgnoringVisibility(navigationBars())` se — **koi hardcoded px nahi**
- gesture nav ON → inset jitni gap, keyboard ke background color mein painted (continuous strip)
- 3-button nav → koi visible gap nahi (adaptive branch, double-padding guard ke saath)
- `Settings.Secure("navigation_mode")` par ContentObserver → mode switch par **live** update, IME restart nahi
- mode detection `config_navBarInteractionMode` se (insets se nahi — Android 15 par
  3-button mode mein bhi `systemGestures.bottom` non-zero aata hai)

Research + AOSP evidence: `docs/research/padding-project/`.

### Toolbar

Web ke suggestion-strip rules aur Gboard ke access-point behaviour ka research
`docs/research/toolbar-project/` mein hai. Android par toolbar pinned tiles dikhata
hai (capacity upar wale rules se), undo/redo pills ke saath.

### Voice toolbar (Gboard "Assistant voice typing toolbar") — implemented

Research: `docs/research/voice-pill-project/` (Gboard 18.3.1-beta APK evidence ke saath).

| Rule | Value | Evidence |
|---|---|---|
| Window strategy | **IME-window widget** — Gboard ke paas `SYSTEM_ALERT_WINDOW` hai hi nahi | APK manifest |
| Touch pass-through | `onComputeInsets()` + `TOUCHABLE_INSETS_REGION` | RESEARCH.md §2.4 |
| States | 5 — panel / horizontal pill / menu / symbols / vertical pill | RESEARCH.md §4 |
| Status text | **"अब बोलें"** (Gboard-exact; "बोल रहा हूं" APK mein 0 matches) | RESEARCH.md §8.3 |
| Menu | 8 items — Gboard ka poora menu (Settings · Show voice commands · Show clipboard · Show translate · Show emoji · Switch to vertical/horizontal toolbar · Symbols · Feedback) + divider ke baad language list | APK strings |
| Mic behaviour | pill ke mic par tap = pause/resume; **pill band nahi hoti** | teardown |
| Menu khulte hi | mic **auto-pause** (Gboard implicit) | RESEARCH.md State 3 |
| ⌨ keyboard icon | pill poori tarah dismiss → normal typing | `widget_hide_keyboard` |
| ˅˅ chevron | pill dikhne ka **ekmatra** trigger | `Collapse keyboard` |
| Persistence | `widget_x_position` / `widget_y_position` / `widget_change_widget_orientation` | APK pref keys |
| Restore | agla text field khulte hi pill wapas (Gboard: "persistent … until you manually bring back the keyboard") | teardown |
| Edge flip | 28dp threshold + 8dp hysteresis, **direction-gated** (chhoti screen par default pill apne aap flip na ho) | owner-approved + regression test |
| Drag lock | "Force the toolbar in horizontal mode and disable dragging" | APK debug flag |
| Tooltip | "Hold and drag to move toolbar" — pehli drag par | APK string |
| Backspace | wahi maujooda 400ms/70ms handler **reuse** (naya timer nahi) | owner standing rule |
| Recognition | `EXTRA_PREFER_OFFLINE` pehle, fail ho to online fallback | owner decision |
| Dock safety | gesture insets ka khayal (padding-project wala controller) | RESEARCH.md §9.5 |
| Mic release | `onFinishInputView`/`onWindowHidden`/`onDestroy` par `destroy()` | battery rule §9.5 |

**Abhi honest gating par kya hai** (hide-nothing): voice *commands* ("delete line",
rephrase), clipboard/translate/emoji panels — menu items dikhte hain aur Gboard ka
verbatim reason dete hain.

---

## 4. Preferences — web ke localStorage keys

Android par `SharedPreferences` (`mg_settings`) use hota hai par **keys wahi** hain,
isliye defaults aur semantics dono apps mein identical hain:

`mg_theme` · `mg_haptic_enabled` · `sg_toolbar` · `mg_pinned_ext_ids` ·
`mg_access_points_count_on_bar` · height / one-handed / language keys.

Live update: `SgPrefs` ke listeners IME ko notify karte hain → height, one-handed,
theme, toolbar turant apply hote hain (restart nahi).
