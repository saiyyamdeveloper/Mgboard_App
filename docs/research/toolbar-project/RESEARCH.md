> **Status: ✅ IMPLEMENTED** — yeh research ab Mgboard app mein lagu ho chuki hai.
> Code: `app/src/main/kotlin/com/mgboard/keyboard/toolbar/` + `ui/KeyboardToolbar.kt` +
> `ui/ToolbarPanels.kt`. Kya-kya bana aur kya honestly gated hai: **`IMPLEMENTATION.md`**.

# Toolbar Deep Research — Gboard 18.3.1.977415014-beta (real APK evidence)

> **Yeh "save-only" research hai** — jaise `padding-project` aur `voice-pill-project` ke liye kiya gaya.
> Sab kuch real Gboard APK ke `resources.arsc` (220,375 strings) + themes (Material3 color keys) +
> 9to5Google/AndroidAuthority reporting se nikala gaya hai. Icon drawable names beta build mein
> obfuscated hain, isliye naming evidence accessibility labels + settings strings + theme element
> names se aayi hai.
>
> **Sabse important baat:** jo aap **"toolbar"** kehte ho, Gboard usse **teen alag naam** deta hai
> context ke hisaab se — *"keyboard toolbar"*, *"suggestion strip"*, aur internally *"access points"*.
> Neeche teeno ka poora mapping hai.

---

## 0. Terminology — pehle naam clear karo

| Aap kahte ho | Gboard ka naam (EN) | Gboard ka naam (HI) | Internal/theme naam |
|---|---|---|---|
| Toolbar | **keyboard toolbar** | **कीबोर्ड टूलबार** | `.keyboard-header-area` |
| Toolbar (technical) | **suggestion strip** | **सुझाव पट्टी** | `.suggestions-strip` |
| Toolbar ke icons | **access points** / access point icons | — | `.access-point-item`, `.label.access-point-item` |
| Toolbar ka "More" menu | **features menu** | **सुविधा मेन्यू** | "Open/Close features menu" |
| Neeche wala keys area | keyboard (body) | — | `.keyboard-body-area` |
| Emoji/GIF panel ka bottom nav | **expression footer / navbar** | **इमोजी वाला पैनल** | `.navbar.for-expression-footer`, `.keyboard-header-area.for-expression-nav` |
| Voice typing ke waqt ka toolbar | **voice toolbar** | टूलबार (same) | `.voice-toolbar`, `.voice-toolbar-container` |

**Verbatim Gboard strings jo proof hain:**

- *"Show the keyboard toolbar while typing"* → HI: **"टाइप करते समय कीबोर्ड टूलबार दिखाएं"**
- *"The keyboard toolbar shows suggestions and provides access to features."*
- *"Access all keyboard features here"* → HI: **"कीबोर्ड की सभी सुविधा यहां पाएं"**
- *"Show suggestions and access features"* → HI: **"सुझाव दिखाएं और सुविधाओं तक पहुंचें"**

> Matlab: **toolbar = suggestions + features access** ka combined strip. Yeh Gboard ki apni definition hai.

---

## 1. Toolbar ki 4 alag-alag cheezein (confusion yahin hoti hai)

Gboard mein "toolbar" word **4 alag widgets** ke liye use hota hai:

| # | Toolbar | Kahan dikhta hai | Evidence |
|---|---|---|---|
| 1 | **Keyboard toolbar / suggestion strip** | Normal typing ke waqt keyboard ke upar | `show_toolbar`, `opt_out_from_toolbar` |
| 2 | **Voice toolbar** | Voice typing pill/widget mode | `show_voice_toolbar`, `opt_out_from_voice_toolbar`, `voice_toolbar_onboarding`, *"Hold and drag to move toolbar"* |
| 3 | **PK toolbar** (physical keyboard) | Hardware/Bluetooth keyboard connected ho | *"PK toolbar, Accessory keyboard"*, `show_pk_toolbar` |
| 4 | **Stylus toolbar** | S Pen / stylus handwriting mode | *"More stylus options"*, *"Open/Close more stylus options list"* |

**Aap jise "toolbar" kehte ho = #1** (keyboard toolbar). Baaki 3 alag modes ke hain.

---

## 2. Keyboard toolbar ka structure (rules — verbatim APK strings)

| Rule | Gboard string (verbatim) |
|---|---|
| Default capacity | *"By default, the suggestions strip shows a maximum of **5** (or **6 on landscape mode**) access point icons. Valid value should between **3 and 8**, inclusive"* |
| Count setting | *"Icons count on suggestions strip"* / *"Define the access point icons count that shown on the suggestion strip."* |
| Order setting | *"Define the order of displayed access point icons, **separated by semicolon**"* → key `access_points_showing_order` |
| Overflow | *"More features closed"* / *"More features opened"* → jo icons fit nahi hote, `more_access_points` mein chale jaate hain |
| Foldable devices | `foldable_access_points_count_on_bar`, `foldable_access_points_showing_order`, `force_enable_horizontal_toolbar_on_foldables` |
| Undo/Redo | *"The Undo and Redo chips appear in the suggestion strip via **access point**, when user edits existing text."* |

**Config keys (APK se directly):**

```
access_points_showing_order            ← semicolon-separated custom order
access_points_count_on_bar             ← 3..8 (default 5 portrait / 6 landscape)
remained_access_points_on_bar
more_access_points
widget_access_point_*                  ← voice widget ke liye alag set
fast_access_bar_*                      ← emoji fast-access bar
show_toolbar / opt_out_from_toolbar / enable_toolbar
enable_writing_tools_icon_in_suggestion_strip
  → "Show writing tools icon in suggestion strip while typing"
enable_show_emoji_key_in_horizontal_toolbar
enable_force_show_horizontal_toolbar_in_foldable
enable_undo_redo_via_access_point
enable_quick_insert
enable_access_point_customization
enable_access_point_state_in_customization
enable_access_point_education
enable_access_point_education_footer   → theme .access-point-education-footer
enable_access_point_education_footer_in_settings
enable_suggestion_strip_ime_action_button / enable_hide_suggestion_strip_ime_action
enable_suggestion_strip_popup_menu     → theme .suggestion-strip-popup-menu-item
enable_suggestions_strip_animation_v2 / enable_suggestions_strip_animation_for_tablet
enable_suggestions_strip_scroll        → theme .suggestions-strip.scroll
enable_strip_trailing_space_divider    → theme .strip-trailing-space.divider
enable_access_point_animation_for_suggestion_strip_in_prime
enable_access_point_animation_for_suggestion_strip_in_non_prime
enable_access_point_animation_for_suggestion_strip_in_non_prime_with_g
enable_ime_action_access_point
enable_ime_switch_access_point
enable_clipboard_content_suggestion
enable_show_clipboard_icon_in_non_prime
enable_auto_hide_keyboard_header
enable_access_point_state_in_customization
enable_stylus_long_press_popup_menu
```

---

## 3. Toolbar ke icons — poora inventory ("access points")

Gboard in icons ko **access points** kehta hai. Proof: permission dialog ke exact patterns
*"Allow **\<name\>** to access …"* — yeh strings sirf access points ke liye banti hain:

| # | Access point (EN) | Hindi (APK) | Kya karta hai | Evidence |
|---|---|---|---|---|
| 1 | **Voice Typing** | **बोली को लिखाई में बदलने की आसान सुविधा** | Mic → voice typing | *"Allow Voice Typing to access your microphone and voice recordings?"*, *"Access Voice Typing"* |
| 2 | **Writing Tools** | **लेखन टूल** | Proofread/Rewrite/SmartEdit entry | *"Access Writing Tools"*, *"Show writing tools icon in suggestion strip while typing"* |
| 3 | **Proofread** | **प्रूफ़रीड** | Spelling/grammar/punctuation | *"Access Proofread"*, *"Proofread this text"* |
| 4 | **Quick Insert** | (Hindi nahi mila — EN fallback) | Contacts se naam/email/phone chip insert | *"Allow Quick Insert to access your contacts?"*, *"Access Quick Insert"* |
| 5 | **Emoji** | **इमोजी** | Emoji/Expression panel kholta hai | *"Open/Close emoji panel"*, *"Show a button on the keyboard toolbar to open emoji keyboard"* |
| 6 | **Clipboard** | **क्लिपबोर्ड** | Clipboard panel | *"Access clipboard"*, *"Show clipboard"* |
| 7 | **Translate** | **अनुवाद** | Translate panel | *"Access translate"*, *"Show translate"* |
| 8 | **Gboard settings** | **सेटिंग** | Settings app kholta hai | *"Access Gboard settings"* |
| 9 | **More / features menu** | **ज़्यादा विकल्प** / **सुविधा मेन्यू** | Baaki sab features ka overflow menu | *"Open/Close features menu"*, *"More features opened/closed"* |
| 10 | **Undo / Redo** | — | Edit chips (text edit ke waqt) | *"The Undo and Redo chips appear in the suggestion strip via access point"* |
| 11 | **IME action** (↵/→/search/send) | — | Editor action button | `enable_ime_action_access_point` |
| 12 | **IME switch** (🌐) | — | Keyboard switch | `enable_ime_switch_access_point` |

> **Note:** `theme`, `gif`, `sticker`, `one_handed_mode`, `handwriting`, `settings` purane versions mein
> direct toolbar icons the — 18.x mein zyada tar **features menu** ke andar ya settings mein shift ho gaye
> (flags like `enable_sticker_access_point` ab sirf flag-level par hain).

---

## 4. Popup / overflow menus (toolbar se khulne wale)

Gboard mein **4 popup menu families** hain (sab ke open/close accessibility labels APK mein hain):

| Menu | EN labels (verbatim) | HI labels (verbatim) |
|---|---|---|
| **Features menu** | *"Open features menu"*, *"Close features menu"*, *"Access all keyboard features here"* | *"कीबोर्ड की सभी सुविधा यहां पाएं"* |
| **More keyboard options** | *"More keyboard options"*, *"…opened"*, *"…closed"*, *"Open/Close more keyboard options list"* | *"कीबोर्ड के ज़्यादा विकल्प"*, *"…खोले गए"*, *"…बंद किए गए"*, *"…की सूची खोलें/बंद करें"* |
| **More voice options** | *"More voice options"* + same 4 variants | *"आवाज़ के ज़्यादा विकल्प"* + same 4 variants |
| **More stylus options** | *"More stylus options"* + same 4 variants | — |

Voice ke extra: *"Open/Close voice command list"*, *"Close voice typing banner"*, *"More options to fix errors"* → HI *"गलतियां ठीक करने के ज़्यादा विकल्प"*.

---

## 5. Panels — toolbar se khulne wale poore panels (aap ka main sawaal)

### 5.1 Expression panel (Emoji/GIF/Stickers) — `.navbar.for-expression-footer`

| Component | Names (verbatim) |
|---|---|
| Panel | *"Emoji panel"*, *"Open emoji panel"*, *"Close emoji panel"* → HI **"इमोजी वाला पैनल"**, **"इमोजी वाला पैनल खोलें/बंद करें"** |
| Tabs | **Emoji**, **GIF**, **Stickers**, **Favorites**, **Recents** |
| Search | `.expression-search-box`, `.expression-search-input-text`, *"Search emoji"*, *"Search stickers"* |
| Categories | `.expression-category-item`, `.expression-category-item-cn`, `.expression-category-item-tablet`, `.expression-text-btn-content` |
| Emoji Kitchen | *"Open/Close Emoji Kitchen"*, `.emoji-kitchen-browse-emoji-panel-search-box` |
| Nav arrow | `.for-expression-navigation.arrow`, *"Open/Close more expressions list"* |
| Footer label | *"Close the emoji panel"*, *"Close the symbols panel"* |

### 5.2 Symbols panel — categories (APK se exact)

**Numbers · Brackets · Arrows · Mathematics · List · Shapes · Emoticons · Recent**

(Hindi footer: *"सिंबल वाला पैनल बंद करें"*)

### 5.3 Baaki panels / modes (sab ke open/close labels APK mein hain)

| Panel / Mode | EN (verbatim) | HI (verbatim) |
|---|---|---|
| **Clipboard** | *"Show clipboard"*, *"Close clipboard panel" (v2)* | **क्लिपबोर्ड** |
| **Translate** | *"Show translate"*, *"Close translate panel"* | **अनुवाद** |
| **Writing Tools** | *"Show Writing Tools"*, *"Close Writing Tools"*, *"Enable Writing Tools Hybrid Mode"* | **लेखन टूल** |
| **Proofread** | *"Show Proofread"*, *"Proofread this text"* | **प्रूफ़रीड** |
| **Select mode** | *"Enter select mode"*, *"Exit select mode"* | — |
| **Edit menu** | *"Open edit menu"*, *"Close edit menu"* | — |
| **Handwriting** | *"Enter handwriting mode"*, *"Exit handwriting mode"* | — |
| **Symbols keyboard** | *"Show symbols keyboard"* | — |
| **More expressions** | *"Open/Close more expressions list"* | — |
| **Voice command list** | *"Open/Close voice command list"* | *"आवाज़ के ज़्यादा विकल्पों की सूची खोलें/बंद करें"* |

---

## 6. Voice toolbar (alag widget — aap ke voice-pill project se related)

| Item | Evidence |
|---|---|
| Auto-start | *"Enable voice toolbar and start voice typing automatically when you tap on a text box."* |
| Docking/drag | *"Hold and drag to move toolbar"* |
| Orientation | *"Switch to horizontal toolbar"* / *"Switch to vertical toolbar"* |
| Minimize | *"To find the toolbar, while dictating, tap minimize on your keyboard."* → HI *"टूलबार खोजने के लिए, बोली को लिखाई में बदलने की सुविधा इस्तेमाल करने के दौरान, अपने कीबोर्ड पर छोटा करें पर टैप करें"* |
| Hide keyboard | *"Hide the keyboard when switching to Voice typing"* → HI *"बोली को लिखाई में बदलते समय कीबोर्ड को छिपाएं"* |
| Back to keyboard | *"Quickly switch from the toolbar to the keyboard. Just tap the keyboard icon."* |
| Onboarding | `voice_toolbar_onboarding_view_count/_click_count`, `voice_toolbar_psa_view_count/_click_count` |
| Theme | `.voice-toolbar`, `.voice-toolbar-container`, `.voice-toolbar-widget` |
| Widget access points | `widget_access_point_*`, `widget_access_points_count_on_bar`, `more_widget_access_points` |

---

## 7. Theme / styling layer (Material3) — toolbar ke colors kahan se aate hain

Toolbar ke visual states **color-state-list selectors** se drive hote hain (yeh names APK mein readable hain):

```
res/color/m3_toolbar_item_color.xml                    ← icon tint
res/color/m3_toolbar_navigation_icon_color.xml
res/color/m3_toolbar_subtitle_color.xml / _surface_container.xml
res/color/m3_toolbar_title_color.xml
res/color/vibrant_m3_toolbar_item_color.xml            ← vibrant theme variant
res/color/m3_widget_popup_bg_color.xml / _content_color.xml / _surface_variant_color.xml
res/color/m3_accessory_candidate_color.xml / _label_color.xml
res/color/m3_accessory_keyboard_bg_color.xml / _body_color.xml / _key_color.xml / _label_color.xml
res/color/m3_accessory_primary_tab_color.xml / _secondary_tab_color.xml
res/color/m3_suggestions_strip_bg_color.xml
res/color/m3_more_candidates_box_color.xml / _icon_color.xml / _label_color.xml
res/color/m3_candidates_container_bg_color.xml
res/color/m3_chip_bg_color.xml / m3_chip_icon_color.xml / m3_chip_label_color.xml
```

**Layout/element names (theme XML se):**

```
.keyboard-header-area                      ← TOOLBAR (yahi aap ka "toolbar" hai)
.keyboard-header-area.panel.v2             ← panel-mode toolbar (clipboard/translate/emoji header)
.keyboard-header-area.for-expression-nav   ← emoji panel ka top nav
.keyboard-body-area                        ← keys
.keyboard-body-area.for-non-prime-body     ← non-primary keyboard (symbols etc.)
.suggestions-strip  / .suggestions-strip.scroll
.candidates-first-page / .candidates-rest-pages / .candidate-holder / .candidate-side-holder
.access-point-item / .label.access-point-item / .access-point-customized-state-indicator
.access-point-education-footer
.fast-access-bar-primary                   ← emoji fast access bar
.chip-item-suggestion-text(.highlight)     ← Quick Insert / undo-redo chips
.navbar.for-expression-footer / .for-expression-navigation.arrow
.divider.vertical.for-side-panel / .for-candidate-side-panel
.strip-trailing-space.divider
.suggestion-strip-popup-menu-item
```

> Icon **drawable file names** is beta build mein obfuscated hain (`res/drawable/xy.xml`),
> isliye icon-level naming ke liye upar diye **accessibility labels** hi authoritative source hain.

---

## 8. Toolbar se related user-facing settings (poori list)

| Setting (EN) | Hindi (APK) |
|---|---|
| Show toolbar | **टूलबार दिखाएं** |
| Show the keyboard toolbar while typing | **टाइप करते समय कीबोर्ड टूलबार दिखाएं** |
| Minimize toolbar and hide suggestions | **टूलबार को छोटा करें और सुझाव छिपाएं** |
| Emojis on toolbar | **टूलबार पर इमोजी** |
| Show a button on the keyboard toolbar to open emoji keyboard | — |
| Show writing tools icon in suggestion strip while typing | — |
| Force the horizontal toolbar | — |
| Hold and drag to move toolbar | — |
| Select suggestion on the toolbar | — |
| Suggestion strip | **सुझाव पट्टी** |
| Icons count on suggestions strip | — |
| Customize features menu | **सुविधा के मेन्यू अपनी पसंद के मुताबिक तैयार करें / बनाएं** |
| Show in suggestion strip | — |
| Quickly switch from the toolbar to the keyboard. Just tap the keyboard icon. | — |

---

## 9. Toolbar ke 3 kaam (Gboard ki apni definition)

1. **Suggestions dikhana** — `.suggestions-strip` par next-word/phrase predictions + inline completions
   (`enable_suggestions_strip_inline_completions_in_non_prime`).
2. **Features ka access dena** — access points (emoji, clipboard, translate, voice, writing tools…).
3. **Contextual chips** — undo/redo, Quick Insert (contacts), emoji suggestions
   (`enable_show_emoji_suggestion`), inline autofill (`enable_inline_autofill_on_suggestions_strip`,
   `enable_suggestions_strip_inline_autofill_with_chips`).

Iske alawa toolbar **IME action button** (send/search/go) aur **IME switch (🌐)** bhi host kar sakta hai.

---

## 10. MgBoard ke liye conclusions (naam kya use karein)

1. **Naam:** user-facing **"टूलबार / Toolbar"** sahi hai (Gboard bhi yahi kehta hai), lekin code mein
   Gboard wali vocabulary use karo: `KeyboardToolbar` / `SuggestionStrip`, items = `AccessPoint`,
   overflow = `FeaturesMenu`. Isse future Gboard-comparison aasan rahega.
2. **Capacity rule copy karo:** default 5 portrait / 6 landscape, valid range 3–8, order
   semicolon-separated list ke roop mein store karo (same as `access_points_showing_order`).
3. **Icon set:** minimum viable = Voice, Emoji, Clipboard, Translate, Settings, More.
   Advanced = Writing Tools, Proofread, Quick Insert, Undo/Redo, IME action, IME switch.
4. **Aapki 5 panels ki list** (history, numbers+symbols, brackets, arrows, math, numbered list)
   Gboard ke saath match karti hai — Gboard symbols categories hain:
   **Numbers, Brackets, Arrows, Mathematics, List, Shapes, Emoticons, Recent**.
   "History" Gboard mein **Recent / Recently used** ke naam se hai.
5. **Har panel ka open/close label + Hindi string** pehle se bana lo (Gboard pattern:
   `"Open X"` / `"Close X"` + `"X panel"`), kyunki accessibility aurTalk ke liye zaroori hai.
6. **Overflow menu zaroori hai** — 3–8 icons ke baad baaki features menu mein jaate hain.
7. **4 popup menus** ka pattern follow karo: features / keyboard / voice / stylus (MgBoard ke liye
   pehle 3 kaafi hain).

---

## 11. Kya cheezein APK se confirm NAHI ho payi

- **Default access point order** ki actual semicolon list (defaults code mein hain, strings mein nahi) —
  runtime ya Gboard settings UI se hi pata chalega.
- **Icon drawable names** (beta build obfuscated).
- **Features menu ke andar ki exact item list** (menu items dynamic/server-driven hain; sirf
  *"Customize features menu"* aur *"Access all keyboard features here"* strings mile).

---

*Sources: `/tmp/gx/all_strings.txt` (Gboard 18.3.1.977415014-beta, 220,375 strings),
`/tmp/gx/brella/reslist.txt` (8,914 resource paths), 9to5Google (2024-01-22, 2025-02-10, 2025-04-15),
AndroidAuthority (voice typing layout).*
