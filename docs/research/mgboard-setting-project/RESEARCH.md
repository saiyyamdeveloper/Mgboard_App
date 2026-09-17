# MgBoard Settings Project — Gboard Settings Page ka Poora Structure (Deep Research)

**Project naam:** `mgboard-setting-project` (owner ke diye naam se)
**Source of truth:** Gboard 18.3.1.977415014-beta — `/tmp/gx/all_strings.txt` (220,375 arsc strings),
`/tmp/en2.txt` (100,768 English-ish lines), `/tmp/allflags.txt` (432 snake_case config names),
`/tmp/gx/reslist.txt` (8,914 resource paths).
**Status:** SAVE-ONLY — implement tabhi jab owner kahe: *"mgboard setting project implement karo"*.

> ## 📘 Yeh project 2 files mein hai
> | File | Kya hai |
> |---|---|
> | **`RESEARCH.md`** (yeh file) | Settings ka **STRUCTURE** — pages, har option ka naam (EN+HI), flags, toggle patterns, search keywords |
> | **`FUNCTIONS-AND-LOGIC.md`** 🆕 | Har setting ka **FUNCTION + LOGIC** — kya karta hai, default value, mutual exclusion, dependency, gating, disabled-reasons, side-effects, live-apply + code-ready Kotlin patterns |
>
> Dono saath padhne se complete picture banti hai: `RESEARCH.md` = *kya hai*, `FUNCTIONS-AND-LOGIC.md` = *kaise kaam karta hai*.

> **Evidence marking convention (important):**
> - 🟢 **EXACT** = `grep -xF` se poora line wahi string hai (100% confirmed Gboard label)
> - 🔵 **ALIAS** = settings-search keywords mein mila (Gboard ke settings search index ka hissa)
> - 🟣 **FLAG** = `enable_*` / snake_case config name mila (feature exist karta hai, UI label alag ho sakta hai)
> - ⚪ **INFERRED** = description/media se infer kiya (APK mein exact label nahi mila)

---

## 0. Executive summary

Gboard settings **ek search bar + 10 main pages** ka tree hai. Har page ke andar toggles/entries hain,
aur har toggle ke 3 cheezein hoti hain: **label + summary (description) + search keywords (aliases)**.

**Main pages (sab 🟢 EXACT):**

```
Preferences · Theme · Languages · Glide typing · Text correction · Voice typing
Emojis, Stickers & GIFs · Clipboard · Personalization · Privacy · Advanced
Physical keyboard · Handwriting · Morse code · Help & feedback · About
```

**Hindi page names (🟢 EXACT):** प्राथमिकताएं · थीम · भाषाएं · उन्नत · निजता · गोपनीयता ·
क्लिपबोर्ड · इमोजी · स्टिकर · शब्दकोश · फ़िज़िकल कीबोर्ड · सहायता और फ़ीडबैक ·
इमोजी, स्टिकर और GIF

---

## 1. Settings kaise khulti hai (entry points)

| Entry | Evidence |
|---|---|
| Toolbar ka **⚙️ Gboard settings** access point | 🟢 `Access Gboard settings` (grid-menu-project §4 item 6) |
| Features menu (⊞ grid) ke andar **Settings** icon | 🟣 `Whether to show the Settings icon in the feature menu.` |
| Keyboard par **G logo** tap → toolbar → gear | ⚪ media (androidguias) |
| **Comma key long-press** → shortcuts → settings | ⚪ media; 🟢 `Show keyboard shortcuts` |
| Android **System settings → Languages & input** | 🟢 `Select <b>%s</b> in your Language & input settings` |
| App icon / launcher se | 🟢 `Show in app list`, 🔵 `App icon, Gboard icon, Hide icon` |
| **Settings search** | 🟢 `Search keyboard`, 🟣 `Search language`, `Search to insert` |

> **Search-first design:** Gboard settings mein search bar hai aur har setting ke saath
> comma-separated **search keywords** store hote hain (🔵 ALIAS lines). Yeh MgBoard ke liye
> sabse copy-worthy pattern hai — neeche §6 mein poori alias table hai.

---

## 2. SETTINGS TREE — page by page, poora inventory

### 2.1 `Preferences` — प्राथमिकताएं 🟢

| # | Setting (EN) | Status | Flag / Alias | Hindi |
|---|---|---|---|---|
| 1 | **Toolbar** | 🟢 | `show_toolbar`, `enable_toolbar`, `opt_out_from_toolbar` | टूलबार / टूलबार दिखाएं |
| 2 | **Show the keyboard toolbar while typing** | 🟢 | — | टाइप करते समय कीबोर्ड टूलबार दिखाएं |
| 3 | **Suggestion strip** | 🟢 | — | सुझाव पट्टी |
| 4 | **Show suggestions and access features** | 🟢 | — | सुझाव दिखाएं और सुविधाओं तक पहुंचें |
| 5 | **Number row** | 🟢 | `enable_number_row` | — |
| 6 | Always show on QWERTY, QWERTZ, and AZERTY layouts | 🟢 | `enable_number_row_in_password` | QWERTY, QWERTZ, और AZERTY लेआउट में फ़िज़िकल कीबोर्ड के सभी बटन दिखाएं |
| 7 | Always show on QWERTY, QWERTZ, and AZERTY for password input | 🟢 | — | — |
| 8 | **Key borders** | 🟢 | `enable_key_border` | — |
| 9 | **Vibrate on keypress** | 🟢 | `enable_vibrate_on_keypress` | कुंजी दबाने पर कंपन (वाइब्रेशन) |
| 10 | Sound on keypress | 🔵🟣 | `enable_sound_on_keypress`, `sound_volume` | — |
| 11 | Popup on keypress | 🟣 | `enable_popup_on_keypress` | — |
| 12 | Character popup, Letter preview, Key pop-up | 🔵 | — | की पॉप-अप |
| 13 | Key long-press delay | 🔵 | alias: `Touch hold duration, Key press delay, Symbol delay` | — |
| 14 | **Keyboard height** | 🟣 | `keyboard_height_ratio`, `keyboard_height_33_mm … 52_mm` | — |
| 15 | The default keyboard height | 🟢 | `set default keyboard height to 33/35/37 mm` | — |
| 16 | Mini keyboard height | 🟢 | `enable_mini_keyboard_height` | — |
| 17 | Keyboard height ratio (portrait / landscape / normal-mode-only / foldable unfolded) | 🟢 | `floating_keyboard_resize_keyboard_custom_body_height_ratio` | — |
| 18 | **Double tap on spacebar to add period followed by a space** | 🟢 | `enable_double_space_period` | — |
| 19 | Double tap period, Spacebar period | 🔵 | — | — |
| 20 | **Auto-capitalization** | 🟢 | `enable_auto_capitalization` | अपने-आप कैपिटल लेटर का इस्तेमाल करना |
| 21 | Capitalize words, Sentence case | 🔵 | — | — |
| 22 | Show a dedicated key to switch languages | 🟢 | — | — |
| 23 | Change languages with a dedicated key. This hides the emoji key. | 🟢 (summary) | — | — |
| 24 | Show a dedicated key to access emoji | 🟢 | `enable_emoji_alt_physical_key` | इमोजी ऐक्सेस करने के लिए |
| 25 | Emoji button, Emoticon key | 🔵 | — | इमोजी की |
| 26 | Show a button on the keyboard toolbar to open emoji keyboard. Only available in unfolded device state. | 🟢 (summary) | — | — |
| 27 | Show row of recently used emoji | 🟢 | — | — |
| 28 | Emoji bar, Quick emoji row | 🔵 | — | इमोजी की लाइन |
| 29 | Emoji ka size | 🟢 (HI) | `keyboard_emoji_scale_setting` | इमोजी का साइज़ / इमोजी का साइज़ बढ़ाएं-कम करें |
| 30 | Use device font size / Use system font instead of Gboard default one. | 🟢 | `keyboard_font_size_setting` | इमोजी और कीबोर्ड के फ़ॉन्ट का साइज़, सिस्टम सेटिंग के हिसाब से तय होता है. |
| 31 | **One-handed mode** | 🟢 | `keyboard_mode_foldable_one_handed_mode` | एक हाथ वाला / एक हाथ से इस्तेमाल करने की सुविधा |
| 32 | One-handed mode shrinks down your keyboard to let you easily type with one hand | 🟢 (summary) | — | एक हाथ से इस्तेमाल करने की सुविधा से कीबोर्ड को छोटा किया जा सकता है… |
| 33 | **Split keyboard** | 🟢 | `layout_9key_split` | — |
| 34 | Floating keyboard / **Floating** | 🟢 | `floating_keyboard_mode_data`, `enable_auto_float_keyboard_in_landscape_mode` | फ़्लोटिंग / फ़्लोटिंग कीबोर्ड |
| 35 | Enable floating keyboard automatically by default when the device is in landscape mode. | 🟢 (summary) | — | — |
| 36 | Drag here to exit floating mode | 🟢 | — | — |
| 37 | Resize | 🟢 | `floating_keyboard_resize_keyboard_*` (5 keys) | ऊपरी/निचले दाएं-बाएं कोने पर कीबोर्ड का आकार बदलें |
| 38 | **Multilingual typing** | 🟢 | `enabled_ime_language_tags` | — |
| 39 | **Show in app list** | 🟢 | alias `App icon, Gboard icon, Hide icon` | — |
| 40 | Show physical keyboard toolbar | 🟢 | `show_pk_toolbar` | — |
| 41 | Show the physical keyboard toolbar while typing. | 🟢 (summary) | — | — |
| 42 | Autocorrect suggestions shown at the cursor when toolbar is minimized | 🟢 | `enable_at_cursor_suggestions_for_pk` | — |
| 43 | Use the toolbar for easy voice typing | 🟢 | `has_shown_voice_toolbar` | — |

### 2.2 `Theme` — थीम 🟢

| # | Setting | Status | Evidence |
|---|---|---|---|
| 1 | **Theme** (page) | 🟢 | `Theme`, `Theme settings` |
| 2 | Create keyboard theme with my image | 🟢 | custom theme builder |
| 3 | Cyan Theme (named themes) | 🟢 | theme pack names |
| 4 | Whether to show the Theme icon in the feature menu. | 🟣 | feature-menu flag |
| 5 | Battery saver theme | 🟣 | `enable_battery_saver_theme` |
| 6 | Dynamic color / Material You | ⚪ | theme `m3_*` + `vibrant_m3_*` selectors, `Color dinámico` etc. |
| 7 | Extra-small-screen theme (won't change keyboard height) | 🟢 (summary) | `Whether to enable the theme for extra small screen…` |
| 8 | Theme icon | 🟢 | `Theme icon` |

### 2.3 `Languages` — भाषाएं 🟢

| # | Setting | Status | Evidence |
|---|---|---|---|
| 1 | **Languages** (page) | 🟢 | `Languages` |
| 2 | **Add keyboard** | 🟢 | `Add keyboard` |
| 3 | Language settings | 🟢 | `Language settings`, `language_settings_key` |
| 4 | Disable languages / layouts | 🟢 | exact line |
| 5 | Enable these languages that were shared with you | 🟢 | — |
| 6 | Only applicable if multiple languages are enabled | 🟢 | — |
| 7 | Search language | 🟣 | `Search language` |
| 8 | Layout / Layouts / Keyboard layout | 🟢 | `Layouts, keyboard shortcuts and modifier keys` |
| 9 | Layout examples | 🟢 | `Bulgarian, Phonetic (Traditional)`, `Chakma, compact`, `Cree (Syllabics)` |
| 10 | Show canonical romanization | 🟢 | — |
| 11 | Use the shift key to toggle between Chinese and English | 🟢 | — |
| 12 | Use half-width space | 🟢 | — |
| 13 | Japanese: Use QWERTY layout for alphabet input mode / when landscape | 🟢 | `japanese_12keys_use_qwerty_layout_for_alphabet` |
| 14 | Add dedicated Digit keyboard in addition to Hiragana and Alphabet keyboard. | 🟢 | — |
| 15 | Layout promo | 🟣 | `layout_promo_display_count/_last_display_timestamp/_result` |

### 2.4 `Glide typing` — ग्लाइड करके टाइप करना 🟢(HI)

| # | Setting | Status | Evidence |
|---|---|---|---|
| 1 | **Glide typing** (page) | 🟢 | `Glide typing` / `Glide Typing` |
| 2 | Glide input | 🟣 | `enable_gesture_input`, `enable_incremental_gesture_input` (+ `_ko`, `_zh_tw`) |
| 3 | Show the suggested word while gesturing | 🟢 | exact line |
| 4 | Glide auto-commit | 🟣 | `enable_gesture_auto_commit` (+ `_zh_tw`) |
| 5 | Show how your finger moves across the keyboard (**trail**) | 🟢 | exact line |
| 6 | Cursor control by glide | 🔵 | `Cursorsteuerung durch Wischen`, `Cursorbediening via swipen` + 🟢 HI `कर्सर को ग्लाइड करने का कंट्रोल` |
| 7 | Press & hold the space bar to move the cursor | 🟢 | exact line |
| 8 | Free cursor by long-press space | 🟣 | `enable_free_cursor_by_long_press_space` |
| 9 | Delete swipe | 🟢(HI) | `ग्लाइड करके शब्द मिटाने की सुविधा`, `"मिटाएं" बटन से बाईं ओर ग्लाइड करके शब्द मिटाएं` |
| 10 | Cursor left/right/up/down | 🟢 | accessibility labels |
| 11 | Drag to move the cursor | 🟢 | exact line |
| 12 | Draw a circle around a letter, word or phrase to select it | 🔵 | stylus select |

### 2.5 `Text correction` 🟢

| # | Setting | Status | Evidence |
|---|---|---|---|
| 1 | **Auto-correction** | 🟢 | `pref_key_auto_correction`, `pref_key_pk_auto_correction` |
| 2 | Autocorrect, Spell correction, Typing correction, Correct words | 🔵 | search aliases |
| 3 | Use auto-correction | 🟢 | exact line |
| 4 | Auto-correction levels (words and sentences) | 🟣 | `pref_key_auto_correction_level_words_and_sentences`, `pref_key_latin_auto_correction_levels`, `pref_key_auto_correction_levels_auto_correction` |
| 5 | **Auto-capitalization** | 🟢 | `enable_auto_capitalization` |
| 6 | Capitalize words, Sentence case | 🔵 | alias |
| 7 | **Next-word suggestions** | 🟢 | exact line |
| 8 | Use previous context to make suggestions | 🟢 | exact line |
| 9 | Show inline suggestion | 🟣 | `pref_key_enable_inline_suggestion` |
| 10 | Show typing text inline before selecting candidates | 🟢 | exact line |
| 11 | Smart Compose | 🟢 | exact line |
| 12 | Grammar checker | 🟣 | `pref_key_enable_grammar_checker` |
| 13 | Post-correction / AC revert | 🟣 | `pref_key_post_correction_trigger_times`, `_undo_times`, `_undo_or_edit_times`, `pref_key_latin_enable_ac_revert`, `pref_key_jarvis_by_word_revert` |
| 14 | Clear all corrected words / Clear all corrected words? | 🟢 | exact lines |
| 15 | Use dictionary words | 🟢 | exact line |
| 16 | Auto-space & smart punctuation | 🟣 | `enable_auto_space_smart_punctuation`, `enable_autospace_after_punctuation`, `enable_auto_space_zh_hk/_zh_tw` |
| 17 | Dynamic diacritic key | 🟣 | `Enable dynamic diacritic key` |
| 18 | Single-character candidates on/off | 🔵 | `Enkelkarakterkandidate, aan/af` |
| 19 | Add punctuation automatically when voice typing | 🟢 | exact line |

### 2.6 `Voice typing` — बोली को लिखाई में बदलना 🟢

| # | Setting | Status | Evidence |
|---|---|---|---|
| 1 | **Voice typing** (page) | 🟢 | exact line |
| 2 | Voice input | 🟣 | `enable_voice_input` |
| 3 | Dictation, Speech to text, Mic button | 🔵 | aliases |
| 4 | Assistant voice typing, NGA, Smart dictation | 🔵 | aliases |
| 5 | Simple dictation, word for word, with some advanced features | 🔵 | aliases (multi-lang) |
| 6 | Enhanced voice typing | 🟣 | `enable_enhanced_voice_typing` + `_auto_punctuation`, `_automatic_language_switching`, `_speech_enhancement` |
| 7 | **Offline speech recognition** | 🟢 | exact line |
| 8 | Offline mode lets you use voice typing even when there's no internet connection… | 🟢 (summary) | exact line |
| 9 | Advanced voice typing features will be available once the language download is complete | 🟢 | exact line |
| 10 | Adjust to the language you're speaking if it's supported and detected… | 🟢 | exact line |
| 11 | Enable auto language switching | 🟣 | `enable_auto_language_switching`, `enhanced_voice_typing_prefer_detect_language` |
| 12 | On-device voice | 🟣 | `enable_ondevice_voice` |
| 13 | **Voice commands** / All voice commands | 🟢 | `All voice commands`, `Show voice commands`, `Close voice command list` |
| 14 | See voice commands<br>and access settings. | 🟢 | exact line |
| 15 | Voice toolbar | 🟢 | `Use the toolbar for easy voice typing`, `has_shown_voice_toolbar`, `last_voice_toolbar_dictate_time` |
| 16 | Hold and drag to move toolbar | 🟢 | exact line |
| 17 | Force the toolbar in horizontal mode and disable dragging | 🟢 | exact line |
| 18 | Enable voice toolbar and start voice typing automatically when you tap on a text box. | 🟢 | exact line |
| 19 | Hide your keyboard while voice typing | 🟢 | exact line |
| 20 | Use your voice to make any edits | 🟢 | exact line |
| 21 | Enable the voice widget | 🟢 | exact line |
| 22 | Voice Typing access / permission | 🟢 | `Access Voice Typing`, `Allow Voice Typing to access your microphone and voice recordings?` |
| 23 | Activate voice typing as default text input method? | 🟢 | exact line |
| 24 | Global direct-to-dictation | 🟣 | `enable_global_direct_to_dictation` |
| 25 | Dictation type (jetson / traditional) | 🟣 | `dictation_type_jetson`, `dictation_type_traditional` |
| 26 | Voice donation (data sharing) | 🟣 | `enable_voice_donation` |
| 27 | Debug audio dump / share debug audio | 🟣 | `dictation_debug_audio_dump`, `dictation_share_debug_audio` |
| 28 | Voice Typing is disabled in Incognito Mode | 🟢 | exact line |
| 29 | Additional module (Dictation) | 🟢 | feature-split label |
| 30 | `pref_key_stop_dictation_after_sending_writing_tools_v2_prompts` | 🟣 | flag |

### 2.7 `Emojis, Stickers & GIFs` — इमोजी, स्टिकर और GIF 🟢

| # | Setting | Status | Evidence |
|---|---|---|---|
| 1 | **Emojis, Stickers & GIFs** (page) | 🟢 | exact line (3 casing variants) |
| 2 | Emoji suggestions, Text to emoji | 🔵 | alias |
| 3 | Emoji suggestions | 🟣 | `pref_key_enable_emoji_suggestion` |
| 4 | Emoji bar, Quick emoji row | 🔵 | alias |
| 5 | Emoji button, Emoticon key | 🔵 | alias |
| 6 | Show row of recently used emoji | 🟢 | exact line |
| 7 | Show stickers and explore options on emoji key tap | 🟢 | exact line |
| 8 | Sticker suggestions | 🟢 | exact line |
| 9 | Sticker predictions while typing | 🟣 | `enable_sticker_predictions_while_typing` |
| 10 | Show topics next to GIF search bar | 🟢 | exact line |
| 11 | **Emoji Kitchen**, Mix emoji, Combine emoji | 🟢🔵 | `Open Emoji Kitchen settings` |
| 12 | Emojify | 🟣 | `enable_emojify` |
| 13 | Emoji → Expression migration | 🟣 | `enable_emoji_to_expression` |
| 14 | Custom stickers / Add sticker pack / Add pack | 🟢 | exact lines |
| 15 | Search emoji / Search stickers / Search for GIF | 🟢 | exact lines |
| 16 | Search emojis, GIFs and more | 🟢 | exact line |
| 17 | Share timestamps | 🟣 | `latest_emoji_share_from_emoji_kb_timestamp`, `latest_gif_…`, `latest_sticker_…` |
| 18 | Search results cache | 🟣 | `emoji_search_result`, `gif_search_result` |

### 2.8 `Clipboard` — क्लिपबोर्ड 🟢

| # | Setting | Status | Evidence |
|---|---|---|---|
| 1 | **Clipboard** (page) | 🟢 | exact line |
| 2 | Clipboard allows you to access your recent copy and paste history | 🟢 (summary) | exact line |
| 3 | Copy paste chips, Clipboard suggestions, Paste from clipboard | 🔵 | aliases |
| 4 | Show recently copied text and images in suggestions bar | 🟢 | exact line |
| 5 | Clipboard opt-in | 🟣 | `clipboard_opt_in`, `clipboard_opt_in_dialog_shown` |
| 6 | Clipboard history retention | 🟣 | `clipboard_history_retention` |
| 7 | Pinned items | 🟣 | `clipboard_pinned_item_char_number`, `_word_number`, `_daily_log`, `clipboard_unpinned_item_threshold_time` |
| 8 | Use the edit icon to pin, add or delete clips. | 🟢 | exact line |
| 9 | **Screenshots in clipboard** | 🟣 | `enable_screenshot_in_clipboard` |
| 10 | Add screenshots to your Gboard clipboard for easier pasting… | 🟢 (summary) | exact line |
| 11 | Clipboard screenshot feature requires full access to your images… | 🟢 | exact line |
| 12 | Show detailed information for a clip item. / Hide detailed information | 🟢 | exact lines |
| 13 | Turn on/off clipboard, Hide clipboard, Search clipboard | 🟢 | exact lines |
| 14 | Clipboard is empty / Once you copy a piece of text, it will show up here. | 🟢 | exact lines |
| 15 | `clipboard_add_entry`, `clipboard_first_shown_time`, `clipboard_last_clicked_chip_timestamp`, `clipboard_latest_shown_time` | 🟣 | config keys |

### 2.9 `Personalization` 🟢

| # | Setting | Status | Evidence |
|---|---|---|---|
| 1 | **Personalization** (page) | 🟢 | exact line |
| 2 | **Learned words** | 🟢 | exact line |
| 3 | **Delete learned words and data** | 🟢 | exact line |
| 4 | Clear all on-device data that Gboard has saved to improve your typing and voice typing experience | 🟢 | exact line |
| 5 | Your learned words will be erased. This operation cannot be undone. To confirm delete, enter the following number to continue. | 🟢 | exact line |
| 6 | Clearing learned words… | 🟢 | exact line |
| 7 | Improve typing and voice typing based on your Gboard usage patterns and corrections… | 🟢 (summary) | exact line |
| 8 | Adapt Gboard to your typing and voice typing usage patterns | 🟢 | exact line |
| 9 | Manage personalization | 🟢 | `<personalizationlink>Manage personalization</personalizationlink>` |
| 10 | Use personalized dicts | 🟣 | `pref_key_use_personalized_dicts` |
| 11 | **Use conversation history as context** | 🟢 | exact line |
| 12 | **Use screen context** | 🟢 | exact line |
| 13 | Writing Tools opt-in | 🟣 | `pref_key_writing_tools_opt_in` |
| 14 | Writing Tools screen-context opt-in (+timestamp, v1) | 🟣 | `pref_key_writing_tools_screen_context_opt_in*` (5 keys) |
| 15 | conv2query | 🟣 | `pref_key_enable_conv2query` |
| 16 | Personalized order / customize | 🟣 | `customized_order_personalize_last_check_time`, `_last_checked_feature` |
| 17 | Jarvis eligibility/opt-in | 🟣 | `pref_key_jarvis_eligible/_opt_in/_opt_in_shown` |
| 18 | Text stylization (internal) | 🟣 | `pref_key_text_stylization_internal` |

### 2.10 `Privacy` — निजता / गोपनीयता 🟢

| # | Setting | Status | Evidence |
|---|---|---|---|
| 1 | **Privacy** (page) | 🟢 | exact line |
| 2 | **Incognito mode** | 🟢 | `Voice typing is disabled in Incognito Mode` |
| 3 | Location | 🟢 | exact line |
| 4 | Improve typing and voice typing based on your Gboard usage patterns and corrections | 🟢 | privacy consent text |
| 5 | Audio snippet donation / retention (18 months, max 15–25s) | 🟢 | exact long strings |
| 6 | Voice donation | 🟣 | `enable_voice_donation` |
| 7 | Delete learned words and data | 🟢 | (Personalization se shared) |
| 8 | Contacts permission (Quick Insert) | 🟢 | `Allow Quick Insert to access your contacts?` |
| 9 | Microphone permission | 🟢 | `Allow Voice Typing to access your microphone and voice recordings?` |
| 10 | Delete data, Clear history, Reset Gboard | 🔵 | aliases |
| 11 | Work/personal profile separation | 🟢 | `Each of your Gboard settings applies to both your personal profile and your work profile…` |

### 2.11 `Advanced` — उन्नत 🟢

| # | Setting | Status | Evidence |
|---|---|---|---|
| 1 | **Advanced** (page) | 🟢 | exact line |
| 2 | **Physical keyboard** | 🟢 | exact line |
| 3 | Access keys and shortcuts without a physical keyboard | 🟢 | exact line (+ tablet-mode variant) |
| 4 | Show keyboard shortcuts | 🟢 | exact line |
| 5 | Layouts, keyboard shortcuts and modifier keys | 🟢 | exact line |
| 6 | Advanced modifier key handling (Shift, Ctrl, Alt, Meta) for PK simulator | 🟢 | exact line |
| 7 | PK simulator setting | 🟣 | `enable_pk_simulator_setting` |
| 8 | PK auto-correction / auto-capitalize | 🟣 | `pref_key_pk_auto_correction`, `latin_pk_auto_capitalize` |
| 9 | Use Kana input instead of Romaji input on the physical keyboard. | 🟢 | exact line |
| 10 | Press Shift+Space to switch language | 🟢 | exact line |
| 11 | Ctrl+ / Ctrl key | 🟢 | `Ctrl+` |
| 12 | **Handwriting** | 🟢 | exact line |
| 13 | Use your stylus to write in any text field… | 🟢 (summary) | exact line |
| 14 | Use stylus / Use the stylus to write in text fields | 🟢 | exact lines |
| 15 | Handwriting tuning | 🟣 | `handwriting_scrollout_delay`, `handwriting_stroke_width_scale`, `handwriting_timeout_ms` |
| 16 | Whether to show the Handwriting icon in the feature menu. | 🟣 | feature-menu flag |
| 17 | Disable stylus toolbar | 🟣 | `disable_stylus_toolbar` |
| 18 | **Morse code** | 🟢 | exact line |
| 19 | Morse tuning | 🟣 | `pref_key_morse_dot_key_assignment`, `_dash_key_assignment`, `_enable_character_commit`, `_enable_word_commit`, `_enable_key_repeat_on_hold`, `_repeat_interval`, `_repeat_start_delay`, `pref_key_latin_morse_character_commit_timeout`, `_word_commit_timeout` |
| 20 | Enable character timeout / Enable word timeout / Enable key repeat | 🟢 | exact lines |
| 21 | Show morse hint card / Hide morse hint card | 🟢 | exact lines |
| 22 | **Display cutout customization** | 🟢🟣 | `Enable display cutout customization` + summary |
| 23 | Adjust the display density dpi for rendering keyboard. | 🟢 | exact line |
| 24 | Use tri-state keyboard | 🟢 | exact line |
| 25 | Enable the text preview | 🟢 | exact line |
| 26 | Show accessibility layout | 🟢 | exact line |
| 27 | Debug Feature Split | 🟢 | exact line |
| 28 | Open source licenses | 🟢 | exact line |
| 29 | Current version | 🟢 | exact line |
| 30 | Create a new bug report | 🟢 | exact line |
| 31 | Autofill | 🟢 | exact line |
| 32 | Text editing | 🟢 | exact line |
| 33 | CursorAnchorInfo | 🟣 | `pref_key_enable_show_cursoranchorinfo` |

### 2.12 `Help & feedback` / `About` — सहायता और फ़ीडबैक 🟢

| # | Setting | Status | Evidence |
|---|---|---|---|
| 1 | **Help & feedback** | 🟢 | exact line |
| 2 | Help / Feedback / About | 🟢 | exact lines |
| 3 | Rate us | 🟣 | `pref_key_has_user_tapped_rate_us` |
| 4 | Current version | 🟢 | exact line |
| 5 | Open source licenses | 🟢 | exact line |
| 6 | jetson feedback | 🟣 | `pref_key_jetson_feedback`, `pref_key_jetson_finish_icon` |

---

## 3. Cross-page features (jo kai pages se link hoti hain)

| Feature | Pages | Evidence |
|---|---|---|
| **Writing Tools** (Proofread / Rewrite) | Text correction, Personalization, Privacy | 🟢 `Writing Tools`, `Proofread`, `Rewrite`; 🟣 `pref_key_writing_tools_*` (7 keys) |
| **Quick Insert** (contacts) | Personalization, Privacy | 🟢 `Quick Insert`; 🟣 `Enable Super Insert` |
| **Translate** | Preferences, Languages | 🟢 `Translate`; 🟣 `pref_key_translate_source_language`, `_target_language`, `_all_sources`, `_all_targets`, `_recent_source_languages`, `_recent_target_languages`, `_accepted_term`, `enable_auto_show_translate` |
| **Toolbar / feature menus** | Preferences, Advanced | 🟢 `Customize feature menus`, `Customize your menu and shortcuts`, `Finish customizing feature menus` |
| **Floating / one-handed / split** | Preferences, Advanced | 🟢 exact lines + `floating_keyboard_resize_*` |

---

## 4. Poora FLAG inventory (`/tmp/allflags.txt` = 432 snake_case names)

### 4.1 `enable_*` flags (69 total; settings-relevant 27)

```
enable_sound_on_keypress          enable_vibrate_on_keypress       enable_popup_on_keypress
enable_key_border                 enable_number_row                enable_number_row_in_password
enable_auto_capitalization        enable_double_space_period       enable_auto_space_smart_punctuation
enable_autospace_after_punctuation enable_auto_space_zh_hk         enable_auto_space_zh_tw
enable_gesture_input              enable_incremental_gesture_input enable_gesture_auto_commit
enable_free_cursor_by_long_press_space
enable_voice_input                enable_ondevice_voice            enable_voice_donation
enable_enhanced_voice_typing      enable_enhanced_voice_typing_auto_punctuation
enable_enhanced_voice_typing_automatic_language_switching
enable_enhanced_voice_typing_speech_enhancement
enable_global_direct_to_dictation enable_auto_language_switching
enable_emoji_alt_physical_key     enable_emoji_to_expression       enable_emojify
enable_sticker_predictions_while_typing
enable_screenshot_in_clipboard    enable_shortcuts_dictionary      enable_mini_keyboard_height
enable_battery_saver_theme        enable_auto_show_translate
enable_pk_simulator_setting       enable_at_cursor_suggestions_for_pk
enable_display_cutout_customization (summary string se)
enable_always_show_dragging_toolbar_tooltip  enable_always_show_pk_toolbar_orientation_tooltip
```

### 4.2 `pref_key_*` (54 total) — opt-in / counters / overrides

```
pref_key_auto_correction                            pref_key_auto_correction_level_words_and_sentences
pref_key_auto_correction_levels_auto_correction     pref_key_latin_auto_correction_levels
pref_key_latin_enable_ac_revert                     pref_key_jarvis_by_word_revert
pref_key_enable_grammar_checker                     pref_key_enable_inline_suggestion
pref_key_enable_emoji_suggestion                    pref_key_enable_show_cursoranchorinfo
pref_key_use_personalized_dicts                     pref_key_enable_conv2query
pref_key_writing_tools_opt_in                       pref_key_writing_tools_enable_pi_override
pref_key_writing_tools_pi_override_enabled_features pref_key_writing_tools_v2_backend_type_override
pref_key_writing_tools_screen_context_opt_in        pref_key_writing_tools_screen_context_opt_in_v1
pref_key_writing_tools_screen_context_opt_in_timestamp
pref_key_writing_tools_screen_context_opt_in_timestamp_v1
pref_key_stop_dictation_after_sending_writing_tools_v2_prompts
pref_key_translate_source_language / _target_language / _all_sources / _all_targets
pref_key_translate_recent_source_languages / _recent_target_languages / _accepted_term
pref_key_morse_dot_key_assignment / _dash_key_assignment / _enable_character_commit
pref_key_morse_enable_word_commit / _enable_key_repeat_on_hold / _repeat_interval / _repeat_start_delay
pref_key_latin_morse_character_commit_timeout / _word_commit_timeout
pref_key_pk_auto_correction                         pref_key_post_correction_trigger_times
pref_key_post_correction_undo_times / _undo_or_edit_times
pref_key_clear_all_corrected_words                  pref_key_contacts_suggestion_notice_posted
pref_key_clear_agentic_dictation_promo_banner_history
pref_key_jarvis_eligible / _opt_in / _opt_in_shown  pref_key_jetson_feedback / _finish_icon
pref_key_has_user_tapped_rate_us                    pref_key_latest_unified_ime_activation_time
pref_key_switch_to_muse                             pref_key_text_stylization_internal
pref_key_ad_backend_override
```

### 4.3 Settings-state config keys (persisted values)

```
keyboard_height_ratio · keyboard_height_{33,35,37,39,47,48,49,52}_mm
keyboard_font_size_setting · keyboard_emoji_scale_setting · enable_mini_keyboard_height
floating_keyboard_mode_data · floating_keyboard_resize_keyboard_{custom_body_height_ratio,
  custom_body_size, left_margin_ratio, padding_bottom, width_ratio}
keyboard_mode_foldable_one_handed_mode · layout_9key_split
clipboard_{opt_in, opt_in_dialog_shown, history_retention, add_entry, pinned_item_char_number,
  pinned_item_word_number, pinned_item_daily_log, unpinned_item_threshold_time,
  first_shown_time, latest_shown_time, last_clicked_chip_timestamp}
language_settings_key · enabled_ime_language_tags
emoji_search_result · gif_search_result · layout_promo_{display_count,last_display_timestamp,result}
latest_{emoji,gif,sticker}_share_from_*_kb_timestamp
has_shown_voice_toolbar · last_voice_dictate_time · last_voice_toolbar_dictate_time
dictation_type_{jetson,traditional} · dictation_debug_audio_dump · dictation_share_debug_audio
disable_stylus_toolbar · handwriting_{scrollout_delay,stroke_width_scale,timeout_ms}
enhanced_voice_typing_prefer_detect_language · customized_order_personalize_last_check{,_checked_feature}
mode_data_proto_preference_migrated · additional_keyboard_theme
japanese_12keys_use_qwerty_layout_for_alphabet · japanese_space_character_form
enable_iw_staggered_layout · show_period_key · sound_volume · system_haptic_settings
```

---

## 5. Toggle labels ka poora set (verbs ke hisaab se — Gboard ka naming pattern)

### `Show X` (39 confirmed)
```
Show a dedicated key to access emoji        Show a dedicated key to switch languages
Show accessibility layout                   Show canonical romanization
Show clipboard                              Show drop-down menu / Show dropdown menu
Show emoji                                  Show emoji keyboard
Show how your finger moves across the keyboard
Show in app list                            Show in suggestion strip
Show indicator to see which key you're pressing
Show keyboard                               Show keyboard shortcuts
Show morse hint card                        Show on keyboard for applicable languages
Show on-screen keyboard                     Show on-screen keyboard while using physical keyboard
Show physical keyboard toolbar              Show recently copied text and images in suggestions bar
Show row of recently used emoji             Show stickers
Show stickers and explore options on emoji key tap
Show suggestions and access features        Show symbols keyboard
Show the keyboard toolbar while typing      Show the suggested word while gesturing
Show toolbar                                Show topics next to GIF search bar
Show translate                              Show typing text inline before selecting candidates
Show voice commands                         Show writing tools
Show writing tools icon in suggestion strip while typing
```

### `Enable / Use / Allow` (confirmed)
```
Enable app markup                     Enable auto float keyboard in landscape mode
Enable character timeout              Enable display cutout customization
Enable dynamic diacritic key          Enable key repeat
Enable languages                      Enable modifier key handler for PK simulator
Enable the text preview               Enable the voice widget
Enable these languages that were shared with you
Enable word timeout
Use auto-correction                   Use conversation history as context
Use device font size                  Use dictionary words
Use half-width space                  Use previous context to make suggestions
Use screen context                    Use stylus to write in text fields
Use system font instead of Gboard default one.
Use the edit icon to pin, add or delete clips.
Use the shift key to toggle between Chinese and English
Use the stylus to write in text fields Use the toolbar for easy voice typing
Use tri-state keyboard                Use voice typing
Use your voice to make any edits      Use QWERTY layout for alphabet input mode
Use Kana input instead of Romaji input on the physical keyboard.
```

### `Add / Hide / Turn on-off / Disable / Clear / Delete`
```
Add keyboard · Add pack · Add sticker pack · Add to dictionary · Add a new item
Add punctuation automatically when voice typing · Add emojis · Add word before/after another word
Hide clipboard · Hide emoji · Hide emoji keyboard · Hide keyboard · Hide more candidates
Hide morse hint card · Hide on-screen keyboard · Hide sign-to-text · Hide symbol(s) keyboard
Hide translate · Hide undo chips · Hide voice commands · Hide writing tools
Hide your keyboard while voice typing · Hide detailed information for the clip item.
Turn on clipboard · Turn off clipboard · Turn on digit mode · Turn off digit mode · Turn on now
Disable languages / layouts
Clear all corrected words · Clear all overrides · Clear all text · Clear override · Clear key assignment
Clear sticker deletion selections · Clear search · Clear query · Clear subject/body/to-cc-bcc
Delete learned words and data · Delete selected items · Delete selected stickers
Delete the last word or a selected word
```

---

## 6. 🔑 Settings SEARCH KEYWORDS (aliases) — Gboard ka sabse copy-worthy pattern

Har setting ke saath comma-separated keywords store hote hain, taaki user search se setting dhoondh sake.
**English aliases jo APK mein mile (🔵):**

| Setting | Aliases (verbatim) |
|---|---|
| App icon visibility | `App icon, Gboard icon, Hide icon` |
| Voice typing engine | `Assistant voice typing, NGA, Smart dictation` |
| Voice typing basic | `Dictation, Speech to text, Mic button` |
| Auto-correction | `Autocorrect, Spell correction, Typing correction, Correct words` |
| Auto-capitalization | `Capitalize words, Sentence case` |
| Clipboard chips | `Copy paste chips, Clipboard suggestions, Paste from clipboard` |
| Quick Insert | `Contact names, People names` |
| Reset / data delete | `Delete data, Clear history, Reset Gboard` |
| Double-space period | `Double tap period, Spacebar period` |
| Toolbar customize | `Drag to reorganize, or place in your shortcut top bar` |
| Stylus select | `Draw a circle around a letter, word or phrase to select it` |
| Emoji Kitchen | `Emoji Kitchen, Mix emoji, Combine emoji` |
| Emoji row | `Emoji bar, Quick emoji row` |
| Emoji key | `Emoji button, Emoticon key` |
| Emoji suggestions | `Emoji suggestions, Text to emoji` |
| Expression page | `Emojis, Stickers & GIFs` |
| Number row | `Always show on QWERTY, QWERTZ, and AZERTY layouts` (+ password variant) |
| Key long-press delay | `Touch hold duration, Key press delay, Symbol delay` |
| Character popup | `Character popup, Letter preview, Key pop-up` |
| Haptics | `Haptic feedback, Key tap vibration, Haptics` |
| Cursor glide | `Cursor control by glide` (DE/NL variants confirmed) |
| Spacebar cursor | `Press & hold the space bar to move the cursor` |
| PK shortcuts | `Press Shift+Space to switch language` |

> **MgBoard lesson:** apne har setting item mein `searchKeywords: List<String>` field rakho.
> Gboard yahi karta hai — isliye uska settings search itna accurate hai.

---

## 7. Hindi strings — ready-to-use (verbatim Gboard)

| EN | HI (verbatim) |
|---|---|
| Preferences | **प्राथमिकताएं** |
| Theme | **थीम** |
| Languages | **भाषाएं** |
| Advanced | **उन्नत** |
| Privacy | **निजता** / **गोपनीयता** |
| Emojis, Stickers & GIFs | **इमोजी, स्टिकर और GIF** |
| Clipboard | **क्लिपबोर्ड** |
| Dictionaries | **शब्दकोश** |
| Personal dictionary | **निजी शब्दकोश** |
| Physical keyboard | **फ़िज़िकल कीबोर्ड** |
| Help & feedback | **सहायता और फ़ीडबैक** |
| Glide typing | **ग्लाइड करके टाइप करना** |
| Glide trail | **ग्लाइड करने का ट्रेल** |
| Delete swipe | **ग्लाइड करके शब्द मिटाने की सुविधा** |
| Cursor control by glide | **कर्सर को ग्लाइड करने का कंट्रोल** |
| Vibrate on keypress | **कुंजी दबाने पर कंपन (वाइब्रेशन)** |
| Key popup | **की पॉप-अप** |
| Auto-capitalization | **अपने-आप कैपिटल लेटर का इस्तेमाल करना** |
| Auto-correction | **स्वतः सुधार** |
| Next-word suggestions | **अगले शब्द के सुझाव** |
| Toolbar | **टूलबार** / **टूलबार दिखाएं** |
| Suggestion strip | **सुझाव पट्टी** |
| One-handed mode | **एक हाथ वाला** / **एक हाथ से इस्तेमाल करने की सुविधा** |
| Floating | **फ़्लोटिंग** / **फ़्लोटिंग कीबोर्ड** |
| Incognito mode | **गुप्त मोड** |
| Voice typing in incognito | **गुप्त मोड में बोली को लिखाई में बदलने की सुविधा काम नहीं करती** |
| Learned words | **सीखे गए शब्द** |
| Delete learned words confirm | **आपके सीखे गए शब्दों को मिटा दिया जाएगा. इस कार्रवाई को पहले जैसा नहीं किया जा सकता. मिटाने की पुष्टि करने के लिए, नीचे दिया गया नंबर दर्ज करें.** |
| Personalization consent | **Gboard को इस्तेमाल करने के आपके पैटर्न और टाइप करते समय किए गए सुधारों के आधार पर, टाइपिंग और बोली को लिखाई में बदलने की सुविधा को बेहतर बनाया जाता है…** |
| Customize feature menus | **अपनी पसंद के मुताबिक मेन्यू और शॉर्टकट सेट करें** |
| Drag to reorganize | **सुविधाओं को फिर से व्यवस्थित करने के लिए उन्हें खींचें और छोड़ें या उन्हें शॉर्टकट टॉप बार में जोड़ें** |
| Morse code | **मोर्स कोड** |
| Handwriting | **हस्तलेखन** |
| Emoji Kitchen | **इमोजी किचन** |
| Search keyboard | (label APK mein `Search keyboard` — HI equivalent: **कीबोर्ड खोजें**) |

---

## 8. MgBoard Settings — implementation blueprint (save-only spec)

### 8.1 Page structure (Gboard-exact, 11 top-level entries)

```kotlin
enum class MgSettingsPage(val titleEn: String, val titleHi: String, val order: Int) {
    PREFERENCES   ("Preferences",            "प्राथमिकताएं",          1),
    THEME         ("Theme",                  "थीम",                    2),
    LANGUAGES     ("Languages",              "भाषाएं",                 3),
    GLIDE_TYPING  ("Glide typing",           "ग्लाइड करके टाइप करना",  4),
    TEXT_CORRECTION("Text correction",       "टेक्स्ट सुधार",          5),
    VOICE_TYPING  ("Voice typing",           "बोली को लिखाई में बदलना",6),
    EXPRESSIONS   ("Emojis, Stickers & GIFs","इमोजी, स्टिकर और GIF",   7),
    CLIPBOARD     ("Clipboard",              "क्लिपबोर्ड",             8),
    PERSONALIZATION("Personalization",       "पर्सनलाइज़ेशन",          9),
    PRIVACY       ("Privacy",                "निजता",                 10),
    ADVANCED      ("Advanced",               "उन्नत",                 11),
    HELP_FEEDBACK ("Help & feedback",        "सहायता और फ़ीडबैक",     12),
    ABOUT         ("About",                  "जानकारी",               13),
}
```

### 8.2 Item data model (Gboard ka 3-part pattern copy karo)

```kotlin
sealed class MgSettingItem {
    abstract val id: String                 // "enable_vibrate_on_keypress"  ← Gboard flag naming
    abstract val labelEn: String            // "Vibrate on keypress"
    abstract val labelHi: String            // "कुंजी दबाने पर कंपन (वाइब्रेशन)"
    abstract val summaryEn: String?         // description (Gboard: har toggle ka summary hota hai)
    abstract val summaryHi: String?
    abstract val searchKeywords: List<String> // 🔵 Gboard alias pattern — search ke liye MUST
    abstract val page: MgSettingsPage
    abstract val dependsOn: String?         // conditional visibility
}

data class ToggleItem(...)  : MgSettingItem()   // SwitchPreference
data class EntryItem(...)   : MgSettingItem()   // opens sub-page (Languages, Theme…)
data class ChoiceItem(...)  : MgSettingItem()   // Keyboard height (33/35/37/39 mm)
data class SliderItem(...)  : MgSettingItem()   // Keyboard height ratio 0.5–2.0
data class ActionItem(...)  : MgSettingItem()   // Delete learned words and data
```

### 8.3 Phase-wise rollout (Gboard-exact priority)

| Phase | Pages | Items | Reason |
|---|---|---|---|
| **1** | Preferences, Theme, Languages | ~25 | Core UX; bina inke keyboard usable nahi |
| **2** | Glide typing, Text correction | ~20 | Typing quality |
| **3** | Voice typing, Expression, Clipboard | ~30 | Feature depth |
| **4** | Personalization, Privacy, Advanced | ~30 | Trust + power users |
| **5** | Help & feedback, About, Settings **search** | ~10 | Polish |

### 8.4 Rules jo Gboard se copy karni hain

1. **Har toggle ka `id` = `enable_<snake_case>`** (Gboard convention) — future flag-parity aasan.
2. **Har item mein `searchKeywords`** rakho (§6 pattern) → settings search banao.
3. **Keyboard height mm-based** ho (33/35/37/39/47/48/49/52 mm) + ratio 0.5–2.0 — Gboard-exact,
   aur yeh `padding-project` ke height model (`total = body + gap`) ke saath directly compatible hai.
4. **Confirm-before-destroy**: "Delete learned words" par number-entry confirmation (Gboard exact UX).
5. **Conditional visibility**: e.g. emoji toolbar button sirf unfolded devices par; number-row
   "Always show on QWERTY…" sirf applicable layouts par; voice typing incognito mein auto-disable.
6. **Consent screens** privacy/personalization ke liye Gboard ke exact 3-link pattern par:
   `Learn more` · `See voice commands` · `Manage personalization`.
7. **Do-not-hardcode strings** — sab `strings.xml` mein EN+HI dono (Gboard 900 languages rakhta hai).

---

## 9. Honest gaps (jo APK se confirm nahi ho paya)

| Gap | Reason |
|---|---|
| Settings tree ka **exact XML hierarchy** (kaun si setting kis page mein) | Gboard ka settings UI **code/server-driven** hai; arsc mein sirf flat strings hain. Page-assignment maine labels + flags + summaries + media se infer kiya hai (⚪ marked) |
| `Sound on keypress`, `Popup on keypress`, `Keyboard height`, `Incognito mode`, `Text shortcuts`, `Emoji suggestions` ke **exact standalone labels** | Flags/aliases confirmed hain, lekin poora-line exact match nahi mila → label wording version-specific ho sakti hai |
| Settings **icons/drawables** | Beta build mein resource names obfuscated |
| Theme page ke **individual theme names** ka poora list | Sirf kuch (`Cyan Theme`) readable |

---

## 10. Related projects

| Project | Path | Relation |
|---|---|---|
| Toolbar inventory | `/home/user/toolbar-project/RESEARCH.md` | Preferences page ka "Toolbar" section |
| ⊞ Grid / features menu | `/home/user/grid-menu-project/RESEARCH.md` | "Customize feature menus" entry |
| Gesture padding | `/home/user/padding-project/` | Keyboard height mm/ratio model yahan use hoga |
| Voice pill | `/home/user/voice-pill-project/` | Voice typing page ka poora section |

---

*Sources: `/tmp/gx/all_strings.txt` (Gboard 18.3.1.977415014-beta, 220,375 arsc strings),
`/tmp/en2.txt` (100,768 English lines), `/tmp/allflags.txt` (432 config names),
`/tmp/gx/reslist.txt` (8,914 resources); HowToGeek (2026-03-08), 9to5Google (2023-06-02),
AndroidPolice (2025-02-28 / 2026-01-25), AndroidGuías (2025-08-28).*
