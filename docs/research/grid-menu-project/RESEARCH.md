# ⊞ Grid Icon = "Features Menu" — Deep Research & Analysis

**Project naam:** `grid-menu-project`  *(pehle `features-menu-project` tha — owner ke kahne par rename)*

> ## 📦 Yeh project ab 3 docs + code + resources mein hai
> | File | Kya hai |
> |---|---|
> | **`RESEARCH.md`** (yeh file) | Naam ka faisla + ⊞ grid menu ke **saare 28 options** (structure/naming) |
> | **`FUNCTIONS-AND-LOGIC.md`** | Har item ka **FUNCTION + LOGIC** — gates, panel-switch sequence, customization flow, promos, error strings, defaults, mutual exclusion |
> | **`INTEGRATION.md`** | MgBoard mein **wiring** (5 steps), 14 Gboard-exact rules, 50+ test checklist, 12 pitfalls, 8-phase order |
> | **`kotlin/`** (4 files, 974 lines) | `GridMenuItem.kt` (29 entries), `GridMenuController.kt`, `GridMenuCustomizer.kt`, `GridMenuPanelHost.kt` |
> | **`res/values{,-hi}/strings.xml`** | EN (Gboard verbatim) + HI (🟢 verbatim / ✍ own translation marked) |

**Naming note:** Gboard khud is menu ko **"features menu"** kehta hai aur icon ki state ko
**"More features"** — dono APK ke 220,375 strings mein verbatim maujood hain. Owner ne project ka naam
**`grid-menu-project`** rakha (media isi icon ko *"grid icon / four-square grid icon"* kehti hai),
isliye **code mein classes `GridMenu*`** hain lekin **user-facing strings Gboard wale hi** hain
(`"More features"` / `"ज़्यादा सुविधाएं"`, `"Open features menu"`).

**Source:** Gboard 18.3.1.977415014-beta — `/tmp/gx/all_strings.txt` (220,375 strings),
`/tmp/en3.txt` (65,703 English lines), `/tmp/allflags.txt` (432 config names), themes + reslist;
HowToGeek / 9to5Google / AndroidPolice.
**Status:** ✅ IMPLEMENTED (files ready) — MgBoard repo mein wire karne ke liye `INTEGRATION.md` dekhein.

---

## 1. Naam ka faisla — sabse pehle yeh clear karo

### 1.1 Aapka proposed naam "Extensions icon" — Gboard mein **kahin nahi hai**

**Hard evidence:** poore `resources.arsc` (220,375 strings, ~900 languages) mein
**"extension" word ka count = 0.**

```
$ grep -icE "extension" all_strings.txt
0
```

"Extensions" terminology **Chrome OS / Chromebook keyboard** aur browser ki hai, Gboard Android ki nahi.
Isliye MgBoard mein "Extensions" naam rakhna Gboard se mismatch karega.

### 1.2 Gboard ka **official** naam (APK se verbatim)

| Layer | Gboard ka naam | Verbatim string |
|---|---|---|
| Menu ka naam | **features menu** | `Open features menu` / `Close features menu` |
| Icon ka state/contentDescription | **More features** | `More features opened` / `More features closed` |
| CTA / promo label | **See more features** | `See more features` |
| Menu ka purpose | — | `Access all keyboard features here` |
| Customize screen | **Customize feature menus** | `Customize feature menus` / `Customise feature menus` |
| Customize subtitle | — | `Customize your menu and shortcuts` |
| Customize finish button | — | `Finish customizing feature menus` |
| Toolbar ka doosra naam | **shortcut top bar** | `Drag to reorganize, or place in your shortcut top bar` |

### 1.3 Media/community jo naam use karti hai (informal)

| Source | Naam |
|---|---|
| HowToGeek | **"Grid"** — *"Tapping this opens the overflow menu for tools that don't fit on the toolbar. You can drag and drop icons to customize it."* |
| 9to5Google | *"a small **grid icon**, which holds all of Gboard's tools in a nice little menu"* |
| AndroidPolice (2026) | *"tap the **four-square grid icon** … to open the full shortcuts menu"* / *"**four-grid menu icon** … full shortcuts panel"* |
| AndroidPolice (2025) | *"the icon that lets you **expand the shortcuts tool grid** holds its position on the left"* |
| Material Design | Icon shape ka naam = **`grid_view`** (purana naam `apps`) |

### 1.4 MgBoard ke liye final naming recommendation

| Level | Naam | Reason |
|---|---|---|
| **Project/folder** | `grid-menu-project` ✅ *(owner ka final choice; pehle `features-menu-project`)* | — |
| **Class** | `GridMenuController` / `GridMenuPanelHost` ✅ *(owner ka choice)* | Gboard strings `FeaturesMenu` wale hi rakhe |
| **Icon ID** | `AccessPoint.MoreFeatures` (grid_view drawable) | Gboard state string se |
| **User-facing (EN)** | "More features" | Gboard-exact |
| **User-facing (HI)** | **"ज़्यादा सुविधाएं"** | Gboard Hindi string verbatim |
| Alias (docs mein) | Grid icon / Grid menu / ⊞ | media vocabulary, samajhne mein aasan |
| ❌ Avoid | "Extensions", "Apps icon" | Gboard mein exist hi nahi karta |

---

## 2. Yeh icon toolbar mein kahan hota hai (position + behaviour)

| Property | Detail | Evidence |
|---|---|---|
| Position | Toolbar (suggestion strip) ke **kinare par fixed** — icon drag se nahi hataya ja sakta | AndroidPolice: *"holds its position on the left"*; 9to5Google: *"on the far left"* |
| Kya karta hai | Jo access points toolbar par fit nahi hote, un sab ka **overflow menu** kholta hai | HowToGeek: *"overflow menu for tools that don't fit on the toolbar"* |
| States | opened / closed (dono ke alag accessibility labels) | `More features opened` / `More features closed` |
| Customizable | Haan — long-press + drag se items toolbar par bhejo ya wapas menu mein | `Drag to reorganize, or place in your shortcut top bar` |
| Toolbar capacity | 5 portrait / 6 landscape (max), valid range 3–8 | `access_points_count_on_bar` |
| Order storage | semicolon-separated | `access_points_showing_order` |
| Overflow storage | — | `more_access_points`, `remained_access_points_on_bar` |
| Customization flag | — | `enable_access_point_customization`, `customized_order_personalize_last_checked_feature` |

> **Relation to `toolbar-project`:** Grid icon = toolbar ka **last access point** (position fixed),
> aur uske andar wahi cheezein hain jo `access_points_showing_order` se hataayi gayi hain.
> Matlab: `toolbar` (strip) aur `features menu` (grid) ek hi system ke do hisse hain.

---

## 3. Customize flow (Gboard ka exact UX)

```
1. Toolbar par ⊞ grid icon tap karo            → "Open features menu"  ("More features opened")
2. Grid mein sab tools dikhte hain
3. Kisi icon ko long-press + drag karke
   upar "shortcut top bar" mein chhodo          → "Drag to reorganize, or place in your shortcut top bar"
   HI: "सुविधाओं को फिर से व्यवस्थित करने के लिए उन्हें खींचें और छोड़ें
        या उन्हें शॉर्टकट टॉप बार में जोड़ें"
4. Toolbar se icon ko wapas grid mein drag karo → remove
5. "Finish customizing feature menus" tap karo  → save
```

Settings-side entry: **`Customize feature menus`** → subtitle **`Customize your menu and shortcuts`**
(HI: *"अपनी पसंद के मुताबिक मेन्यू और शॉर्टकट सेट करें"*)

Per-icon visibility flags (APK mein sirf in 3 ke descriptions readable hain — proof ki menu items
flag-controlled hote hain):

```
Whether to show the Handwriting icon in the feature menu.
Whether to show the Settings icon in the feature menu.
Whether to show the Theme icon in the feature menu.
```

---

## 4. ⊞ GRID MENU KE ANDAR KE **SAARE OPTIONS** (poora inventory)

Yeh list APK ke **exact single-line labels** se bani hai (`grep -xF` = poora line wahi string),
media icon-inventory se cross-verify ki gayi hai. 4 tiers mein baanta gaya hai — MgBoard ke liye
priority clear rahe.

### TIER 1 — Core (Gboard mein hamesha/sabse common; MgBoard ke liye MUST)

| # | EN label (verbatim) | Hindi (verbatim / source) | Kya karta hai | Icon |
|---|---|---|---|---|
| 1 | **Clipboard** | **क्लिपबोर्ड** | Copy/paste history panel (`Show clipboard` → *क्लिपबोर्ड दिखाएं*) | 📋 |
| 2 | **Translate** | **अनुवाद** | Real-time translate panel (`Show translate` → *अनुवाद दिखाएं*) | 🌐 |
| 3 | **Emoji** | **इमोजी** | Expression/emoji panel (`Show emoji` → *इमोजी दिखाएं*) | 😊 |
| 4 | **GIF** / **GIFs** | — (GIF) | GIF search panel | 🎞️ |
| 5 | **Sticker** / **Stickers** | **स्टिकर** (`स्टिकर दिखाएं`) | Sticker/Bitmoji panel | 🏷️ |
| 6 | **Settings** / **Gboard settings** | **सेटिंग** | Keyboard settings kholta hai — *flag: "Settings icon in the feature menu"* | ⚙️ |
| 7 | **Theme** | **थीम** | Keyboard themes picker — *flag: "Theme icon in the feature menu"* | 🎨 |
| 8 | **Voice typing** | **बोली को लिखाई में बदलने की आसान सुविधा** | Mic se dictation (toolbar par default right side fixed) | 🎤 |
| 9 | **Text editing** | — | Cursor arrows + select/copy/paste controls (media: *"Uppercase I"*) | 🔠 / ↕ |

### TIER 2 — Layout & keyboard modes

| # | EN label (verbatim) | Hindi (verbatim) | Kya karta hai |
|---|---|---|---|
| 10 | **One-handed** / **One-handed mode** | **एक हाथ वाला** / *एक हाथ से इस्तेमाल करने की सुविधा* | Keyboard ek side shrink (`Exit one-handed mode`) |
| 11 | **Floating** | **फ़्लोटिंग** / *फ़्लोटिंग कीबोर्ड* | Movable floating keyboard (`Exit floating keyboard`, *"Drag here to exit floating mode"*) |
| 12 | **Resize** | — (*ऊपरी/निचले दाएं-बाएं कोने पर कीबोर्ड का आकार बदलें*) | Height/position adjust (media: *"dashed-line square"*) |
| 13 | **Split** | — | Split keyboard (tablet) |
| 14 | **Handwriting** | **हस्तलेखन** (संबंधित भाषा strings) | Handwriting/stylus input — *flag: "Handwriting icon in the feature menu"* |
| 15 | **Symbols** | — | Symbols keyboard (`Show symbols keyboard`, *"Open the on-screen symbols keyboard"*) |
| 16 | **Number row** | — | Top par numbers ki row (setting) |
| 17 | **Morse code** | **मोर्स कोड** | Morse input layout |

### TIER 3 — AI / Writing Tools family

| # | EN label (verbatim) | Hindi (verbatim) | Kya karta hai |
|---|---|---|---|
| 18 | **Writing Tools** | **लेखन टूल** | AI writing hub (`Show writing tools`, `Close Writing Tools`, *"Enable Writing Tools Hybrid Mode"*) |
| 19 | **Proofread** | **प्रूफ़रीड** | Spelling/grammar/punctuation (`Show Proofread`, `Access Proofread`) |
| 20 | **Rewrite** | — | Tone/length rewrite (Writing Tools ke andar) |
| 21 | **Quick Insert** | — | Contacts se naam/email/phone chip (`Allow Quick Insert to access your contacts?`) |
| 22 | **Emoji Kitchen** | — | Emoji mashup creator (`Open Emoji Kitchen settings`) |
| 23 | **More expressions** | — | Extra expression list (`Open/Close more expressions list`) |
| 24 | **Personal dictionary** | — | Apne words/shortcuts |

### TIER 4 — Utility / misc

| # | EN label (verbatim) | Hindi | Kya karta hai |
|---|---|---|---|
| 25 | **Undo** / **Redo** | — | Edit chips — *"appear in the suggestion strip via access point"* |
| 26 | **Scan text** | — | Camera se real-world text scan (media: *"Viewfinder"*) |
| 27 | **Search** | — | Google search / `Search to insert` |
| 28 | **Share** | — | Gboard Play Store link share (media-verified icon) |

**Total: 28 confirmed labels** (TIER 1 = 9, TIER 2 = 9, TIER 3 = 7, TIER 4 = 4).

> **Important nuance:** yeh sab labels APK mein maujood hain, lekin **kaun sa item grid mein dikhega**
> device/version/flags/region par depend karta hai (e.g. Handwriting/Settings/Theme ke explicit
> feature-menu flags hain). Grid = *available tools ka superset*; toolbar = user ne jo drag kiya.

---

## 5. Features menu vs "More X options" menus — farq samjho

Gboard mein **4 alag popup menus** hain; grid icon sirf #1 hai:

| Menu | Trigger | Labels |
|---|---|---|
| **Features menu** ← ⊞ grid icon | Toolbar ka fixed grid icon | `Open/Close features menu`, `More features opened/closed` |
| **More keyboard options** | Physical-keyboard / keyboard context | `Open more keyboard options`, `…opened`, `…closed` |
| **More voice options** | Voice toolbar / pill | `Open more voice options`, `Show voice commands` |
| **More stylus options** | Stylus mode | `Open more stylus options` |

Voice pill ke andar ka menu (aapke `voice-pill-project` se link): Settings · Show voice commands ·
Show clipboard · Show translate · Show emoji · Switch to vertical toolbar · Feedback.

---

## 6. Theme / styling layer (grid popup kaise render hota hai)

Grid popup widget ke theme element names (APK themes se):

```
.widget-content-wrapper.entry-menu
.widget-popup-menu-entry-header-label        ← menu ka header
.widget-popup-menu-entry-label               ← har item ka label
.widget-popup-menu-entry-end-icon            ← item ka trailing icon
.widget-popup-menu-entry-shortcuts-key       ← item ka shortcut key hint
.icon.widget-popup-menu-entry-shortcuts-key
.item-ripple.widget-popup-menu-item          ← ripple
.widget-popup-menu-entry-end-icon.non-linear-scale
```

Colors (Material3 selectors, readable names):

```
res/color/m3_widget_popup_bg_color.xml
res/color/m3_widget_popup_content_color.xml
res/color/m3_widget_popup_surface_variant_color.xml
widget_popup_menu_item_highlight_color       ← arsc color key
color_popup_menu_label                       ← arsc color key
mtrl_popupmenu_overlay_color                 ← Material base
```

Toolbar side ke related elements:

```
.keyboard-header-area                ← toolbar
.access-point-item                   ← har icon
.access-point-customized-state-indicator   ← customize mode ka indicator
.access-point-education-footer       ← "See more features" jaisi education strip
.fast-access-bar-primary
.suggestions-strip
```

> Grid icon ka **drawable file name** is beta build mein obfuscated hai
> (`res/drawable/xx.xml`), isliye icon-level naming ke liye Material ka public naam
> **`grid_view`** use karo — shape wahi 4-square grid hai.

---

## 7. Hindi strings jo MgBoard ko copy karni chahiye (verbatim Gboard)

| Use | Hindi (Gboard verbatim) |
|---|---|
| Menu kholna (TalkBack) | **कीबोर्ड की सभी सुविधा यहां पाएं** |
| Menu ka naam | **ज़्यादा सुविधाएं** / सुविधा मेन्यू |
| Opened state | **ज़्यादा सुविधाएं देखें** (`See more features`) |
| Customize instruction | **सुविधाओं को फिर से व्यवस्थित करने के लिए उन्हें खींचें और छोड़ें या उन्हें शॉर्टकट टॉप बार में जोड़ें** |
| Customize screen title | **अपनी पसंद के मुताबिक मेन्यू और शॉर्टकट सेट करें** |
| Customize (alt) | **सुविधा के मेन्यू अपनी पसंद के मुताबिक तैयार करें / बनाएं** |
| Toolbar | **कीबोर्ड टूलबार** / **टूलबार दिखाएं** |
| Items | क्लिपबोर्ड · अनुवाद · इमोजी · स्टिकर · सेटिंग · थीम · एक हाथ वाला · फ़्लोटिंग कीबोर्ड · मोर्स कोड · लेखन टूल · प्रूफ़रीड · हस्तलेखन |

---

## 8. MgBoard implementation blueprint (spec — abhi save-only)

### 8.1 Data model

```kotlin
enum class FeatureMenuItem(val id: String, val labelEn: String, val labelHi: String,
                           val tier: Int, val iconRes: Int) {
    CLIPBOARD   ("clipboard",    "Clipboard",    "क्लिपबोर्ड", 1, R.drawable.ic_clipboard),
    TRANSLATE   ("translate",    "Translate",    "अनुवाद",     1, R.drawable.ic_translate),
    EMOJI       ("emoji",        "Emoji",        "इमोजी",      1, R.drawable.ic_emoji),
    GIF         ("gif",          "GIF",          "GIF",        1, R.drawable.ic_gif),
    STICKER     ("sticker",      "Sticker",      "स्टिकर",     1, R.drawable.ic_sticker),
    SETTINGS    ("settings",     "Settings",     "सेटिंग",     1, R.drawable.ic_settings),
    THEME       ("theme",        "Theme",        "थीम",        1, R.drawable.ic_theme),
    VOICE       ("voice_typing", "Voice typing", "बोली को लिखाई में बदलने की आसान सुविधा", 1, R.drawable.ic_mic),
    TEXT_EDIT   ("text_editing", "Text editing", "टेक्स्ट एडिटिंग", 1, R.drawable.ic_text_edit),
    ONE_HANDED  ("one_handed",   "One-handed",   "एक हाथ वाला", 2, R.drawable.ic_one_handed),
    FLOATING    ("floating",     "Floating",     "फ़्लोटिंग",   2, R.drawable.ic_floating),
    RESIZE      ("resize",       "Resize",       "आकार बदलें", 2, R.drawable.ic_resize),
    SPLIT       ("split",        "Split",        "स्प्लिट",     2, R.drawable.ic_split),
    HANDWRITING ("handwriting",  "Handwriting",  "हस्तलेखन",    2, R.drawable.ic_handwriting),
    SYMBOLS     ("symbols",      "Symbols",      "सिंबल",       2, R.drawable.ic_symbols),
    NUMBER_ROW  ("number_row",   "Number row",   "नंबर पंक्ति",  2, R.drawable.ic_number_row),
    MORSE       ("morse",        "Morse code",   "मोर्स कोड",   2, R.drawable.ic_morse),
    WRITING_TOOLS("writing_tools","Writing Tools","लेखन टूल",   3, R.drawable.ic_writing_tools),
    PROOFREAD   ("proofread",    "Proofread",    "प्रूफ़रीड",   3, R.drawable.ic_proofread),
    REWRITE     ("rewrite",      "Rewrite",      "रीराइट",      3, R.drawable.ic_rewrite),
    QUICK_INSERT("quick_insert", "Quick Insert", "क्विक इंसर्ट", 3, R.drawable.ic_quick_insert),
    EMOJI_KITCHEN("emoji_kitchen","Emoji Kitchen","इमोजी किचन", 3, R.drawable.ic_emoji_kitchen),
    MORE_EXPR   ("more_expressions","More expressions","और एक्सप्रेशन", 3, R.drawable.ic_more_expressions),
    DICT        ("personal_dict","Personal dictionary","व्यक्तिगत शब्दकोश", 3, R.drawable.ic_dict),
    UNDO        ("undo",         "Undo",         "अनडू",        4, R.drawable.ic_undo),
    REDO        ("redo",         "Redo",         "रीडू",        4, R.drawable.ic_redo),
    SCAN_TEXT   ("scan_text",    "Scan text",    "टेक्स्ट स्कैन करें", 4, R.drawable.ic_scan),
    SEARCH      ("search",       "Search",       "खोजें",       4, R.drawable.ic_search),
    SHARE       ("share",        "Share",        "शेयर करें",   4, R.drawable.ic_share),
}
```

### 8.2 Rules (Gboard-exact)

1. Grid icon toolbar ka **fixed access point** hai — drag se hataaya nahi ja sakta.
2. Toolbar capacity: **5 portrait / 6 landscape**, valid **3–8**.
3. Order + membership **semicolon-separated string** mein persist karo (`access_points_showing_order`).
4. Jo toolbar mein nahi → automatically grid mein (`more_access_points`).
5. Grid item long-press → drag → toolbar mein drop = promote; toolbar se grid mein drop = demote.
6. Har item ka `contentDescription` = `"Open <label>"` / `"Close <label>"` pattern (Gboard-exact),
   aur menu ke liye `"Open features menu"` / `"Close features menu"` + `"More features opened/closed"`.
7. Per-item visibility flag rakho (Gboard: Handwriting/Settings/Theme ke alag flags hain).
8. Customize mode ka indicator: `.access-point-customized-state-indicator` jaisa overlay.

### 8.3 Suggested MgBoard Phase-1 grid (10 items)

`Clipboard · Emoji · GIF/Sticker · Translate · Theme · Settings · Text editing · One-handed · Floating · Voice typing`
— baaki Tier 3/4 Phase-2 mein.

---

## 9. Kya confirm NAHI ho paya (honest gaps)

| Gap | Reason |
|---|---|
| Grid menu ka **exact default item order** | Defaults code/server-driven hain; arsc mein sirf labels hain |
| **Icon drawable names** (`grid_view.xml` etc.) | Beta build mein resource names obfuscated |
| Kaun se items kis device/region par dikhte hain | Flag + server config par depend |
| "Extensions" naam ka koi Gboard usage | **0 hits** — naam Gboard ka hai hi nahi |

---

## 10. Related projects (cross-links)

| Project | Path | Relation |
|---|---|---|
| Toolbar inventory | `/home/user/toolbar-project/RESEARCH.md` | Grid icon = toolbar ka access point #9 ("More") |
| Gesture padding | `/home/user/padding-project/` | Toolbar ke neeche keyboard body ka bottom gap |
| Voice pill | `/home/user/voice-pill-project/` | Voice toolbar ka apna popup menu ("More voice options") |

---

*Sources: `/tmp/gx/all_strings.txt` (Gboard 18.3.1.977415014-beta, 220,375 arsc strings),
`/tmp/gx/reslist.txt` (8,914 resource paths), HowToGeek (2026-03-08), 9to5Google (2023-06-02),
AndroidPolice (2025-02-28, 2026-01-25), AndroidGuías (2025-08-28).*
