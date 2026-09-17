# ⊞ GRID MENU — FUNCTIONS & LOGIC (deep analysis)

**Project:** `grid-menu-project` (pehle `features-menu-project` — owner ke kahne par rename)
**Companion:** `RESEARCH.md` (naam + 28 items ki list). **Yeh file = har item ka FUNCTION + LOGIC.**
**Source:** Gboard 18.3.1.977415014-beta — `/tmp/en3.txt` (65,703 strict-English lines),
`/tmp/allflags.txt` (432 config names), `/tmp/gx/reslist.txt` (theme selectors).

> **Evidence:** 🟢 EXACT (verbatim Gboard string) · 🟣 FLAG (config key) · 🎨 THEME (theme element) · ⚪ INFERRED
> **Target:** MgBoard mein **Gboard-jaisa same** behaviour.

---

# PART 0 — GRID MENU KA CORE MECHANISM

## 0.1 Grid menu kya hai (Gboard ki definition)

```
🟢 "Access all keyboard features here"        ← menu ka purpose
🟢 "Open features menu" / "Close features menu"
🟢 "More features opened" / "More features closed"   ← icon ki 2 states
🟢 "See more features"                        ← CTA
🟢 "Access point icon order"                  ← setting ka naam
```

**Mechanism (1 line):** toolbar (`suggestion strip`) par **max 5 (portrait) / 6 (landscape)** access
points fit hote hain; **baaki sab grid menu mein** chhup jaate hain. Grid = overflow container +
customization hub.

```
🟢 "By default, the suggestions strip shows a maximum of 5 (or 6 on landscape mode) access point
    icons. Valid value should between 3 and 8, inclusive"
🟢 "Define the access point icons count that shown on the suggestion strip."
🟢 "Define the order of displayed access point icons, separated by semicolon"
🟣 access_points_showing_order · access_points_count_on_bar · remained_access_points_on_bar
🟣 more_access_points
```

## 0.2 Grid icon **fixed** hai (drag se nahi hataya ja sakta)

⚪ Media-verified (AndroidPolice: *"the icon that lets you expand the shortcuts tool grid holds its
position on the left"*). Gboard isko hataane ka koi option nahi deta — yeh **system access point** hai.

**MgBoard rule:** `GridMenuAccessPoint` ko `removable = false` rakho.

## 0.3 Render layer — menu kaise banta hai

```
🎨 .widget-content-wrapper.entry-menu              ← menu container
🎨 .widget-popup-menu-entry-header-label           ← header ("More features")
🎨 .widget-popup-menu-entry-label                  ← har item ka label
🎨 .widget-popup-menu-entry-end-icon               ← trailing icon
🎨 .widget-popup-menu-entry-shortcuts-key          ← PK shortcut hint
🎨 .icon.widget-popup-menu-entry-shortcuts-key
🎨 .item-ripple.widget-popup-menu-item             ← ripple
🎨 .widget-popup-menu-entry-end-icon.non-linear-scale
🎨 res/color/m3_widget_popup_bg_color.xml · _content_color.xml · _surface_variant_color.xml
🟣 widget_popup_menu_item_highlight_color          ← highlighted item
🟣 color_popup_menu_label
```

> **Note:** `shortcuts-key` element ka matlab — grid item ke saath **physical-keyboard shortcut** bhi
> dikhaya ja sakta hai. `enable_stylus_long_press_popup_menu` stylus variant hai.

## 0.4 Customization mode ka **exact flow** (Gboard-jaisa)

```
STEP 1  Toolbar par ⊞ tap
        → "Open features menu"  (state: "More features opened")

STEP 2  Kisi item ko LONG-PRESS
        → "Hold and drag to customize"  🟢
        → customization mode ON
        → 🎨 .access-point-customized-state-indicator  har item par dikhta hai

STEP 3  Drag karke:
        (a) grid → toolbar  = PROMOTE   (toolbar par add)
        (b) toolbar → grid  = DEMOTE    (toolbar se remove)
        (c) grid ke andar   = REORDER
        → "Drag to reorganize, or place in your shortcut top bar"  🟢
        → HI: "सुविधाओं को फिर से व्यवस्थित करने के लिए उन्हें खींचें और छोड़ें
               या उन्हें शॉर्टकट टॉप बार में जोड़ें"  🟢

STEP 4  "Finish customizing feature menus" tap  🟢
        → order persist: access_points_showing_order (semicolon-separated)
        → 🟣 customized_order_personalize_last_checked_feature
        → 🟣 customized_order_personalize_last_check_time
```

**Settings-side entry (same cheez ka doosra raasta):**
```
🟢 "Customize feature menus"  /  "Customise feature menus"
🟢 "Customize your menu and shortcuts"   ← subtitle
🟢 "Finish customizing feature menus"    ← confirm button
🟣 enable_access_point_customization
🟣 enable_access_point_state_in_customization
```

## 0.5 Capacity / overflow logic (MgBoard ko exact copy karna hai)

| Rule | Value | Evidence |
|---|---|---|
| Portrait capacity | **5** | 🟢 |
| Landscape capacity | **6** | 🟢 |
| Valid range | **3 – 8 inclusive** | 🟢 |
| Order storage | semicolon-separated string | 🟢 |
| Overflow destination | `more_access_points` (grid menu) | 🟣 |
| Remaining on bar | `remained_access_points_on_bar` | 🟣 |
| Foldable override | `foldable_access_points_count_on_bar`, `foldable_access_points_showing_order` | 🟣 |
| Force horizontal | `enable_force_show_horizontal_toolbar_in_foldable`, *"Force the horizontal toolbar"* | 🟢🟣 |

**Algorithm:**
```
visible = order.take(capacity)         // capacity = 5 ya 6 (orientation)
overflow  = order.drop(capacity) + allItemsNotIn(order)
grid      = overflow (fixed order) + gridIcon (always last/first, non-removable)
```

## 0.6 Promotion / education logic (grid items ke apne promos hote hain)

Gboard har naye access point ke liye **promotion banner + dismiss** rakhta hai:

```
🟢 "Close undo access point promotion"
🟢 "Copy & paste multiple items? Try Clipboard"          ← Clipboard ka promo
🟢 "Emoji fast-access row introduction is closed"
🟢 "Showing emoji fast-access row introduction"
🟢 "Close emoji fast-access row introduction"
🟢 "Remove emoji fast-access row. Not clickable until the introduction tooltip is closed."
🟣 enable_access_point_education
🟣 enable_access_point_education_footer
🟣 enable_access_point_education_footer_in_settings
🎨 .access-point-education-footer
```

**State machine (MgBoard ke liye):**
```
NOT_SHOWN → SHOWING → CLOSED(permanent)
jab tak CLOSED na ho, "Remove" action CLICKABLE NAHI hota  🟢
```

## 0.7 Undo/Redo chips ka special rule

```
🟢 "The Undo and Redo chips appear in the suggestion strip via access point,
    when user edits existing text."
🟣 enable_undo_redo_via_access_point
🟢 "Hide undo chips"
```
**Logic:** undo/redo **static menu item nahi** — yeh **contextual chip** hai jo text edit hone par
strip par aata hai. Grid mein iska entry sirf *enable/disable* ke liye hai.

---

# PART 1 — ITEM-BY-ITEM FUNCTION + LOGIC (saare 28)

## TIER 1 — Core

### 1.1 📋 Clipboard
| Aspect | Detail |
|---|---|
| Action | Clipboard panel kholta hai (keyboard body replace) |
| Panel theme | 🎨 `.keyboard-header-area.panel.v2`, `.keyboard-clipboard-item.panel.v2`, `.keyboard-clipboard-popup.panel`, `.background.clipboard-item-checker.panel.v2`, `.tooltip.positive_button.clipboard.panel.v2` |
| Entry gate | Field clipboard support karta ho — 🟢 *"%1$s doesn't allow pasting images here"* |
| Retention | 🟢 *"keeping them for one hour"*; 🟢 *"Unpinned clips will be deleted after 1 hour."* |
| Pin logic | 🟢 *"Touch and hold a clip to pin it."*; 🟣 `clipboard_pinned_item_char_number/_word_number/_daily_log`, `clipboard_unpinned_item_threshold_time` |
| Edit mode | 🟢 *"Use the edit icon to pin, add or delete clips."* |
| Opt-in | 🟣 `clipboard_opt_in`, `clipboard_opt_in_dialog_shown` (pehli baar consent) |
| Screenshots | 🟣 `enable_screenshot_in_clipboard`; 🟢 *"Add screenshots to your Gboard clipboard for easier pasting."* |
| Permission | 🟢 *"Clipboard screenshot feature requires full access to your images. Update the permission in System settings > Apps > Gboard > Permissions"* |
| Strip integration | 🟢 *"Show recently copied text and images in suggestions bar"* |
| Promo | 🟢 *"Copy & paste multiple items? Try Clipboard"* |
| Empty | 🟢 *"Clipboard is empty"* / *"Once you copy a piece of text, it will show up here."* |
| Close | 🟢 `Hide clipboard` / `Turn off clipboard` |

### 1.2 🌐 Translate
| Aspect | Detail |
|---|---|
| Action | Translate panel — source/target language + live translate |
| Panel theme | 🎨 `.label.translate.language.panel` |
| State | 🟣 `pref_key_translate_source_language`, `_target_language`, `_all_sources`, `_all_targets`, `_recent_source_languages`, `_recent_target_languages`, `_accepted_term` |
| Logic | 🟢 *"Current source language for translation is %s."* / *"Current target language for translation is %s."* |
| Auto-show | 🟣 `enable_auto_show_translate` — foreign text detect hone par apne aap |
| Consent | 🟣 `pref_key_translate_accepted_term` (pehli baar terms) |
| Close | 🟢 `Hide translate` |

### 1.3 😊 Emoji
| Aspect | Detail |
|---|---|
| Action | Expression panel (Emoji tab) |
| Open/Close | 🟢 `Open emoji panel` / `Close emoji panel` / `Close the emoji panel` |
| Panel nav | 🎨 `.navbar.for-expression-footer`, `.keyboard-header-area.for-expression-nav`, `.for-expression-navigation.arrow` |
| Tabs | Emoji · GIF · Stickers · Favorites · Recents |
| Search | 🟢 `Search emoji`; 🟣 `emoji_search_result`; error 🟢 *"Still loading emoji search data. Try again later."* |
| Fast-access row | 🟢 `Emoji fast-access row` + introduction state machine (§0.6); 🎨 `.fast-access-bar-primary` |
| Dedicated key conflict | 🟢 *"Access emoji key with a dedicated key. **This hides the language switch key.**"* |
| Toolbar button gate | 🟢 *"Show a button on the keyboard toolbar to open emoji keyboard. **Only available in unfolded device state.**"* |
| Scale | 🟣 `keyboard_emoji_scale_setting` |
| Empty | 🟢 *"You haven't used any emojis yet"* |

### 1.4 🎞️ GIF
| Aspect | Detail |
|---|---|
| Action | Expression panel ka GIF tab |
| Topics | 🟢 *"Show topics next to GIF search bar"* |
| Search | 🟢 `Search for GIF` / `Search for GIFs` |
| Cache | 🟣 `gif_search_result` |
| Error | 🟢 *"Failed to fetch GIFs."* |
| Empty | 🟢 *"You haven't used any GIFs yet"* |
| Share track | 🟣 `latest_gif_share_from_gif_kb_timestamp` |

### 1.5 🏷️ Sticker
| Aspect | Detail |
|---|---|
| Action | Expression panel ka Sticker tab |
| Tap behaviour | 🟢 *"Show stickers and explore options on emoji key tap"* |
| Packs | 🟢 `Add sticker pack`, `Add pack`, `Custom stickers`, `Created sticker`, `Back to browse packs` |
| Predictions | 🟣 `enable_sticker_predictions_while_typing`; 🟢 `Sticker suggestions` |
| Reorder | 🟢 `Close sticker reorder activity` |
| Delete | 🟢 `Delete selected stickers`, `Clear sticker deletion selections` |
| Search | 🟢 `Search stickers` / `Search for Sticker` |

### 1.6 ⚙️ Settings
| Aspect | Detail |
|---|---|
| Action | **Gboard Settings Activity launch** (IME ke andar nahi — alag activity) |
| Label | 🟢 `Access Gboard settings` |
| Flag | 🟢 *"Whether to show the Settings icon in the feature menu."* |
| Field gate | 🟢 *"Gboard settings are not available in this input field"* |
| Extra | 🟢 `Open Emoji Kitchen settings`, `Open source licenses`, `Current version` |

### 1.7 🎨 Theme
| Aspect | Detail |
|---|---|
| Action | Theme picker panel |
| Flag | 🟢 *"Whether to show the Theme icon in the feature menu."*; 🟢 `Theme icon` |
| Options | 🟢 `Default Gboard`, `Default dark theme`, `Default auto theme`, `Default system`, `Cyan Theme`, `Create keyboard theme with my image` |
| Storage | 🟣 `additional_keyboard_theme`, `Default theme file name`, `Default dark theme file name`, `Default themes directory` |
| Battery saver | 🟢 *"Switch to dark theme in battery saver"*; 🟣 `enable_battery_saver_theme`; rationale 🟢 *"Gboard can save power on OLED screens by going dark"* |
| Field gate | 🟢 *"Theme settings are not available in this input field"* |
| Keytop impact | 🎨 `.keytop.dark.*`, `.pill-shaped-if-bordered`, `.extra-padding`, `.2rows-key` |

### 1.8 🎤 Voice typing
| Aspect | Detail |
|---|---|
| Action | Voice typing start (mic) — keyboard → voice mode |
| Permission | 🟢 *"Gboard needs access to the microphone in order to enable voice typing."*; 🟢 *"Allow Voice Typing to access your microphone and voice recordings?"* |
| Auto-start | 🟢 *"Automatically start voice typing when keyboard is shown"* / *"Enable voice toolbar and start voice typing automatically when keyboard is shown"* |
| Locked mode | 🟢 *"Double-tap the mic to continue voice typing until you tap it again, close the keyboard or say 'Stop'"*; 🟢 *"long pressing the microphone to start voice dictation in locked mode"* |
| Offline | 🟢 *"Offline mode lets you use voice typing even when there's no internet connection, but you may get slightly different text."*; model 🟢 *"Faster Voice Typing has been installed (100 MB) and now works offline."* |
| Language gate | 🟢 *"Advanced voice typing features will be available once the language download is complete"*; 🟢 *"Voice Typing is currently not available in %s"* |
| Auto language | 🟢 *"Adjust to the language you're speaking if it's supported and detected…"*; condition 🟢 *"works if you have previously set up and used at least 2 languages that support advanced voice typing features"* |
| Busy error | 🟢 *"Can't start. Microphone in use."* |
| Incognito gate | 🟢 *"Voice typing is disabled in Incognito Mode"* |
| Field gate | 🟢 *"Seamless voice typing is not available in this input field"* |
| Split APK | 🟢 *"Additional module (Dictation)"*; 🟣 `on_demand_feature_split`, `dictation_feature_split.apk` |
| Flags | 🟣 `enable_voice_input`, `enable_ondevice_voice`, `enable_enhanced_voice_typing` (+3 sub), `enable_global_direct_to_dictation` |

### 1.9 🔠 Text editing
| Aspect | Detail |
|---|---|
| Action | Edit menu — cursor arrows + select/copy/paste |
| Open/Close | 🟢 `Open edit menu` / `Close edit menu` |
| Select mode | 🟢 `Open select mode` / `Enter select mode` / `Exit select mode` |
| Disabled states | 🟢 *"Disabled because empty editor"*, *"Disabled because no text selected"*, *"Disabled because items are selected."*, *"Disabled because there is no enough room for the dialog."* |
| Cursor keys | 🟢 `Cursor up/down/left/right`, *"Move the cursor to the beginning/end"* |
| Actions | 🟢 `Select all`, `Select text`, `Cut`, `Delete`, `Select Items` |
| PK link | 🟢 *"When enabled, Gboard stops handling the CTRL+Z key sequence. Instead, the app will handle it."* |

---

## TIER 2 — Layout & keyboard modes

### 1.10 ✋ One-handed
```
🟢 "One-handed mode shrinks down your keyboard to let you easily type with one hand"
🟢 "Switch to left-handed keyboard" / "Switch to right-handed keyboard"
🟢 "Exit one handed mode" / "Exit one-handed mode"
🟢 "Do you want to turn on one-handed mode?"        ← confirm dialog
🟣 keyboard_mode_foldable_one_handed_mode
LOGIC: 3 sub-states = OFF | LEFT | RIGHT  (mutually exclusive)
```

### 1.11 🪟 Floating
```
🟢 "Exit floating keyboard"
🟢 "Drag here to exit floating mode"
🟢 "Enable floating keyboard automatically by default when the device is in landscape mode."
🟢 "Pinch to scale, drag to move"                   ← gesture
🟢 "Finish changing keyboard size and position"     ← resize mode exit
🟢 "Change keyboard size or position"
🟣 floating_keyboard_mode_data
🟣 floating_keyboard_resize_keyboard_{custom_body_height_ratio, custom_body_size,
                                     left_margin_ratio, padding_bottom, width_ratio}
🟣 enable_auto_float_keyboard_in_landscape_mode
```

### 1.12 ↔️ Resize
```
🟢 "Change the size of the keyboard in the top-left corner"  (4 corner variants)
🟢 "Current keyboard does not support resizing"     ← gate
🟢 "Finish changing keyboard size and position"
🟣 keyboard_height_ratio (0.5–2.0), keyboard_height_{33,35,37,39,47,48,49,52}_mm
LINK: padding-project ka height model (total = body + gap) yahin se drive hota hai
```

### 1.13 ⬌ Split
```
🟢 "Split keyboard"
🟢 "When the keyboard is set to split layout, some keys will be duplicated on both sides"
🟢 "Default input area width of split keyboard (in dp)"
🟣 layout_9key_split
GATE: tablet/foldable par hi meaningful
```

### 1.14 ✍️ Handwriting
```
🟢 "Use your stylus to write in any text field. Your handwriting will be converted to text
    that you can edit or delete."
🟢 "Commit handwritten text immediately" / "Convert words immediately while writing"
🟢 "Switch to half screen handwriting" / "Switch to full screen handwriting"
🟢 "Downloading stylus handwriting model"           ← download gate
🟢 "Error creating handwriting model for %s"        ← error state
🟢 Gestures: "Draw a circle around a letter, word or phrase to select it"
             "Scratch out a letter, word or phrase to delete it"
             "Draw down, then %s with your stylus to move text to a new line"
             "Use your stylus to draw a caret or arrow where you want to add new text"
🟢 "The app doesn't support the handwriting gesture here"   ← app gate
🟢 "Handwriting demo is not available in this input field"  ← field gate
🟢 Flag: "Whether to show the Handwriting icon in the feature menu."
🟣 handwriting_{scrollout_delay, stroke_width_scale, timeout_ms}, disable_stylus_toolbar
```

### 1.15 🔣 Symbols
```
🟢 "Open the on-screen symbols keyboard" / "Show symbols keyboard" / "Hide symbols keyboard"
🟢 "Back to letter keyboard"                        ← exit logic
🟢 "Symbols mode"
CATEGORIES: Numbers · Brackets · Arrows · Mathematics · List · Shapes · Emoticons · Recent
🎨 .keyboard-body-area.for-non-prime-body           ← non-prime keyboard
```

### 1.16 🔢 Number row
```
🟣 enable_number_row, enable_number_row_in_password
🟢 "Always show on QWERTY, QWERTZ, and AZERTY layouts"
🟢 "Always show on QWERTY, QWERTZ, and AZERTY for password input"
🟢 "Turn on digit mode" / "Turn off digit mode"
LOGIC: yeh toggle toolbar item NAHI, settings toggle hai — grid mein "keyboard layout" entry se link
```

### 1.17 📡 Morse code
```
🟢 "Control the Morse keyboard with external switches. This is where you assign switches to
    the dot key function" (+ dash key variant)
🟢 Character timeout: "How long Gboard waits before converting a Morse code sequence into a
    letter. The default timeout is 1 second."
🟢 "Enable character timeout" / "Enable word timeout" / "Enable key repeat"
🟢 "Show morse hint card" / "Hide morse hint card"
🟣 pref_key_morse_{dot_key_assignment, dash_key_assignment, enable_character_commit,
                  enable_word_commit, enable_key_repeat_on_hold, repeat_interval,
                  repeat_start_delay}
🟣 pref_key_latin_morse_{character_commit_timeout, word_commit_timeout}
🟢 "Clear key assignment"
🎨 .keytop.for-bottom-key.for-action-key-holder.for_morse_keyboard
```

---

## TIER 3 — AI / Writing Tools family

### 1.18 ✍️ Writing Tools
```
🟢 "Show Writing Tools" / "Close Writing Tools"
🟢 "Enable Writing Tools Hybrid Mode"
🟢 GATE: "Enter some text to use writing tools"     ← empty editor par disable
🟢 GATE: "Advanced features are currently unavailable for apps in a work profile."
🟢 GATE: "Not available in your current device language. Change your device language to
          match your Gboard language to use this."
🟢 "Open menu for reporting Writing Tools results"
🟢 "Select content to report"
🟢 "Can't use this tool at the moment. Please try again later."
🟢 "Failed to process results"
🟢 Consent: "When you use this feature, your text, audio input and personal dictionary will be
             temporarily processed by Google. Rambler can make mistakes, so double-check it."
🟣 pref_key_writing_tools_{opt_in, enable_pi_override, pi_override_enabled_features,
                          v2_backend_type_override, screen_context_opt_in(+_v1,+_timestamp)}
🎨 .bg.writing-tools-show-more
```

### 1.19 ✓ Proofread
```
🟢 "Access Proofread" / "Show Proofread" / "Proofread this text"
🟢 "Correct typos, grammar, and punctuation with just a tap. See suggestions after you type a message."
🟢 GATE: "Can't proofread text in this field"
🟢 GATE: "Gboard doesn't support proofreading for one of the languages that you're using"
🟢 Consent: "Your use of the proofreading feature in Gboard is subject to the Generative AI
             Terms of Service."
🟣 pref_key_enable_grammar_checker, pref_key_auto_correction_level_words_and_sentences
```

### 1.20 🔁 Rewrite
```
🟢 "Rewrite"
🟢 "Open the list of supported voice rewrite command options"
🟢 Voice-driven: "Use a friendlier tone" / "Add happy emoji"  (genaicommand)
🟢 "More options to fix errors"
LOGIC: Writing Tools ke andar ka sub-action; tone/length options
```

### 1.21 👤 Quick Insert
```
🟢 "Allow Quick Insert to access your contacts?"    ← permission dialog
🟢 "Access Quick Insert"
🟢 "Access information from contacts for suggestions"
🟢 "Turn on contacts suggestions."
🟢 "Improve voice typing with more accurate name spelling?"
🟢 Warning: "Double-check suggestions before sharing personal info"
🟢 "Enable Super Insert"                            ← naya variant
🟣 pref_key_contacts_suggestion_notice_posted
🎨 .chip-item-suggestion-text(.highlight)           ← chips
```

### 1.22 🍳 Emoji Kitchen
```
🟢 "Open Emoji Kitchen settings" / "Open Emoji Kitchen" / "Close Emoji Kitchen"
🟢 "Emoji Kitchen sticker for %1$s and %2$s emoji"
🟢 "Search emoji kitchen"
🎨 .emoji-kitchen-browse-emoji-panel-search-box
```

### 1.23 ➕ More expressions
```
🟢 "Open more expressions list" / "Close more expressions list"
🎨 .expression-category-item · .expression-category-item-tablet · .expression-text-btn-content
```

### 1.24 📖 Personal dictionary
```
🟢 "You don't have any words in the personal dictionary. Tap '+' to add a word."
🟢 "Add to dictionary"
🟣 pref_key_use_personalized_dicts, enable_shortcuts_dictionary
```

---

## TIER 4 — Utility

### 1.25 / 1.26 ↩️ Undo / ↪️ Redo
```
🟢 "The Undo and Redo chips appear in the suggestion strip via access point,
    when user edits existing text."
🟢 "Hide undo chips"
🟢 "There are no more actions to undo" / "There are no more actions to redo"
🟢 "Close undo access point promotion"
🟣 enable_undo_redo_via_access_point
LOGIC: contextual chips — edit history stack par depend; stack khali → disabled
```

### 1.27 📷 Scan text
```
🟢 "Gboard needs access to the camera in order to enable Scan text."
LOGIC: camera permission → viewfinder → OCR → text insert
```

### 1.28 🔍 Search / 🔗 Share
```
🟢 "Search to insert" / "Search keyboard" / "Search recently visited URLs"
🟢 "Share"  → Play Store link (⚪ media-verified)
🟢 "Open image in external browser" / "Failed to open the link by the default browser"
```

---

# PART 2 — GLOBAL LOGIC RULES (sab items par lagu)

## 2.1 Item click ka standard sequence

```
1. GATE CHECK   → permission? language? field? device-state? profile? incognito?
                  fail → disabled reason show (§2.3), action mat karo
2. PANEL SWITCH → keyboard body ko panel se replace
                  🎨 .keyboard-header-area  →  .keyboard-header-area.panel.v2
                  🎨 .keyboard-body-area    →  panel body
3. HEADER UPDATE→ panel ka apna header (title + close button)
                  🟢 "Close the emoji panel" / "Close the symbols panel"
4. STATE SAVE   → 🟢 "Back to previous state" (wapas jaane ke liye)
5. CLOSE        → 🟢 "Back to letter keyboard" / "Back to previous state"
```

## 2.2 Keyboard-mode state machine

```
LETTER (prime) ──┬── SYMBOLS        ("Symbols mode" / "Back to letter keyboard")
                 ├── EXPRESSION     (Emoji/GIF/Sticker)
                 ├── CLIPBOARD      (.panel.v2)
                 ├── TRANSLATE      (.label.translate.language.panel)
                 ├── WRITING_TOOLS
                 ├── EDIT / SELECT  ("Exit select mode")
                 ├── HANDWRITING    (half/full screen)
                 ├── MORSE          (.for_morse_keyboard)
                 ├── ONE_HANDED     (LEFT | RIGHT)
                 ├── FLOATING       (+ resize sub-mode)
                 └── SPLIT
🟢 "Default keyboard mode on phone or tablet screen"
🟢 "Default keyboard mode on foldable unfolded screen"
🟢 "Sets default keyboard mode for foldable inner screen or tablet screen.
    For tablet devices, please set both configs using the same value"
🟢 "The keyboard mode will be enforced to normal mode if the default value is set to 1 and
    the preference visibility is set to invisible… only works on phone devices."
```

## 2.3 Error / disabled strings (menu actions ke liye ready)

```
🟢 "Command not available in this app"
🟢 "Command not supported. Check the options."
🟢 "Can't use this tool at the moment. Please try again later."
🟢 "Can't use this tool right now. Try again later."
🟢 "Can't start. Microphone in use."
🟢 "Can't proofread text in this field"
🟢 "Can't show suggestions for this. Please try something else."
🟢 "Can't connect to the server. Try again later."
🟢 "Can't connect. Retry offline."  /  "Can't connect. Tap icon to retry."
🟢 "Can't connect. Check your Wi-Fi or mobile network and try again."
🟢 "Can't reach Google right now"
🟢 "Couldn't load suggestions"  /  "Failed to process results"  /  "Failed to fetch GIFs."
🟢 "Failed to download this language"
🟢 "Something went wrong. Please try again."
🟢 "Disabled because empty editor" / "…no text selected" / "…items are selected."
     / "…opt-in is disabled" / "…there is no enough room for the dialog."
🟢 "Gboard settings are not available in this input field"
🟢 "Theme settings are not available in this input field"
🟢 "This feature is not available in this field"
🟢 "Current keyboard does not support resizing"
```

## 2.4 Mutual exclusion (grid items ke beech)

| A on | B ka effect | Evidence |
|---|---|---|
| Dedicated **language** key | **emoji** key hide | 🟢 |
| Dedicated **emoji** key | **language switch** key hide | 🟢 |
| One-handed LEFT | One-handed RIGHT off | 🟢 (2 alag strings) |
| Floating mode | Split/one-handed nahi | ⚪ |
| `keyboard_height_mm` set | `keyboard_height_ratio` ignore | 🟢 |
| Cursor-glide OFF | spacebar = trackpad | 🟢 |
| Incognito ON | Voice typing disabled | 🟢 |
| Toolbar horizontal-force ON | dragging disabled | 🟢 *"Force the toolbar in horizontal mode and disable dragging"* |

## 2.5 Related toolbar/strip toggles jo grid se control hoti hain

```
🟢 "Show toolbar" · "Show the keyboard toolbar while typing"
🟢 "Minimize toolbar and hide suggestions"
🟢 "Show physical keyboard toolbar" / "Show the physical keyboard toolbar while typing."
🟢 "Force the horizontal toolbar"
🟢 "Hold and drag to move toolbar" / "Hold and drag to customize"
🟢 "Switch to horizontal toolbar" / "Switch to vertical toolbar"
🟢 "Select suggestion on the toolbar"
🟢 "Show writing tools icon in suggestion strip while typing"
🟢 "Quickly switch from the toolbar to the keyboard. Just tap the keyboard icon."
🟣 enable_always_show_dragging_toolbar_tooltip · enable_always_show_pk_toolbar_orientation_tooltip
```

---

# PART 3 — CONFIRMED DEFAULTS (Gboard-exact values)

| Value | Default | Evidence |
|---|---|---|
| Access points on bar | 5 portrait / 6 landscape | 🟢 |
| Valid count range | 3–8 inclusive | 🟢 |
| Order format | semicolon-separated | 🟢 |
| Grid icon removable | **NO** (fixed) | ⚪ media |
| Clipboard retention | 1 hour (unpinned) | 🟢 |
| Morse character timeout | 1 second | 🟢 |
| Keyboard height ratio | 1.0 (0.5–2.0) | 🟢 |
| Keyboard height presets | 33/35/37/39/47/48/49/52 mm | 🟣 |
| Offline voice model | 100 MB | 🟢 |
| Customization trigger | long-press + drag | 🟢 |
| Education dismiss | permanent (CLOSED) | 🟢 |

---

# PART 4 — MgBoard IMPLEMENTATION (Gboard-jaisa same)

## 4.1 Files

| File | Kaam |
|---|---|
| `kotlin/GridMenuItem.kt` | 28 items ka enum + har item ka metadata (action type, gates, panel, flags) |
| `kotlin/GridMenuController.kt` | Menu build + capacity/overflow logic + open/close + gate evaluation + error strings |
| `kotlin/GridMenuCustomizer.kt` | Long-press drag customization (promote/demote/reorder) + semicolon persistence |
| `kotlin/GridMenuPanelHost.kt` | Panel switch state machine + header/close handling |
| `INTEGRATION.md` | MgBoard mein kahan wire karna hai + test checklist |

## 4.2 Design decisions (Gboard-exact)

1. **Naam:** code mein `GridMenu*` (owner ka naam), strings mein Gboard wale hi —
   `"More features"` / `"ज़्यादा सुविधाएं"`, `"Open features menu"`, `"Access all keyboard features here"`.
2. **Capacity:** 5/6, range 3–8, semicolon order — bilkul Gboard.
3. **Grid icon:** `removable = false`, position fixed.
4. **Gates:** 9 types (§PART 0/2) — har item par evaluate, fail par Gboard ka exact disabled string.
5. **Promotion state machine:** NOT_SHOWN → SHOWING → CLOSED.
6. **Panel switch:** `.keyboard-header-area.panel.v2` jaisa header + "Back to previous state".
7. **Live apply:** koi restart nahi — SharedPreference listener.
8. **Hindi:** saare strings EN+HI dono (Gboard verbatim Hindi jahan available).

---

# PART 5 — HONEST GAPS

| Gap | Reason |
|---|---|
| Grid ka **exact default item order** | Defaults code/server-driven; arsc mein sirf labels |
| Icon **drawable names** | Beta build obfuscated → Material `grid_view` use karo |
| Features menu ke andar **kaun sa item kis device par** | Flag + server config par depend |
| Har item ka **boolean default** | Code mein hai, strings mein nahi |
| Menu ka **grid layout** (columns/rows count) | Theme se infer nahi ho paya |

---

*Sources: `/tmp/en3.txt`, `/tmp/allflags.txt`, `/tmp/gx/all_strings.txt`, `/tmp/gx/reslist.txt`
(Gboard 18.3.1.977415014-beta); HowToGeek 2026-03-08, 9to5Google 2023-06-02,
AndroidPolice 2025-02-28 & 2026-01-25.*
