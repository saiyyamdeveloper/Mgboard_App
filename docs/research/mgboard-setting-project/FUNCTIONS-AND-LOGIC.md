# MgBoard Settings — FUNCTIONS & LOGIC (Gboard ka actual behaviour)

**Companion to:** `RESEARCH.md` (structure + labels). **Yeh file = har setting ka FUNCTION aur LOGIC.**
**Source:** Gboard 18.3.1.977415014-beta — `/tmp/en3.txt` (65,703 strict-English lines) + flags + themes.

> **Evidence marking:** 🟢 EXACT (verbatim Gboard string) · 🟣 FLAG (config key) · ⚪ INFERRED
> **Rule:** jahan Gboard ka exact behaviour string mila hai, wahan 🟢 + verbatim quote diya hai.

---

# PART 0 — GLOBAL SETTINGS LOGIC (sabse zaroori)

Yeh 8 rules Gboard ke **poore settings system** par lagte hain. MgBoard ko yeh pehle implement karne chahiye.

## 0.1 Preference storage + live update

| Aspect | Gboard behaviour | Evidence |
|---|---|---|
| Storage | `SharedPreferences` — 3 naming families: `enable_*` (bool), `pref_key_*` (opt-in/state), plain snake_case (values) | 🟣 432 config names |
| Live apply | Setting change ka **turant** asar — keyboard restart nahi hota | ⚪ (Gboard UX) |
| Migration | `mode_data_proto_preference_migrated` — purane prefs ko proto format mein migrate karta hai | 🟣 |
| Per-profile | *"Each of your Gboard settings applies to both your personal profile and your work profile, but neither profile can access suggestions, clipboard content, dictionaries or recent emojis associated with the other profile"* | 🟢 |
| Org policy | *"Your organization doesn't allow you to change the '%s' preference."* → admin-managed prefs lock ho jaati hain | 🟢 |

## 0.2 🔒 MUTUAL EXCLUSION logic (ek on → doosra auto-hide)

Gboard ki sabse important settings-logic. Verbatim:

| Setting A | Setting B | Logic |
|---|---|---|
| *"Change languages with a dedicated key. **This hides the emoji key.**"* | `Show a dedicated key to access emoji` | 🟢 dono ek saath nahi |
| *"Access emoji key with a dedicated key. **This hides the language switch key.**"* | `Show a dedicated key to switch languages` | 🟢 reverse direction |
| *"Open emoji panel with a separate key. **This closes the language switching panel.**"* (uz variant) | — | 🟢 same rule |
| `Only use Flick input and disable toggle (Keitai) input.` | `Toggle only` | 🟢 *"This setting cannot be used at the same time as Toggle only."* |
| `Toggle only` | `Flick only` | 🟢 *"cannot be used at the same time as 'Flick only'"* |
| `keyboard_height_*_mm` (default height) | `keyboard_height_ratio` | 🟢 *"The keyboard height ratio will be ignored if this value is set."* |
| `keyboard_height_ratio` | default height | 🟢 *"If the default keyboard height is set, this value will be ignored."* |
| Cursor-control-by-glide | Spacebar trackpad | 🟢 *"When disabled, use swipe on space bar for trackpad"* |
| Keyboard mode enforce | preference visibility | 🟢 *"The keyboard mode will be enforced to normal mode if the default value is set to 1 **and** the preference visibility is set to invisible… only works on phone devices."* |

**MgBoard rule:** har `ToggleItem` mein `mutuallyExclusiveWith: List<String>` field rakho —
ek on hone par doosra auto-disable + UI se hide.

## 0.3 🚧 GATING logic — setting kab *available* hoti hai

Gboard 6 tarah ke gate lagata hai:

| Gate | Verbatim string | Effect |
|---|---|---|
| **Language gate** | *"Advanced features aren't available in your current Gboard language. Learn more about supported languages"* | Voice typing advanced / Proofread kuch languages par off |
| **Device-language gate** | *"Not available in your current device language. Change your device language to match your Gboard language to use this."* | Feature disable + instruction |
| **Work-profile gate** | *"Advanced features are currently unavailable for apps in a work profile. Switch to your personal apps to use this."* | Writing Tools etc. work profile mein nahi |
| **Incognito gate** | *"Voice typing is disabled in Incognito Mode"* | Voice typing auto-off |
| **Input-field gate** | *"Gboard settings are not available in this input field"* / *"Theme settings are not available in this input field"* / *"Handwriting demo is not available in this input field"* / *"Seamless voice typing is not available in this input field"* / *"This feature is not available in this field"* / *"Cannot show keyboard shortcuts in this input field"* | Field type par depend |
| **Proofread language gate** | *"Gboard doesn't support proofreading for one of the languages that you're using"* | Proofread disable |
| **Device-state gate** | *"Show a button on the keyboard toolbar to open emoji keyboard. **Only available in unfolded device state.**"* | Foldable par conditional |
| **Permission gate** | *"Gboard needs access to the microphone in order to enable voice typing."* / *"Gboard needs access to the camera in order to enable Scan text."* / *"Clipboard screenshot feature requires full access to your images. Update the permission in System settings > Apps > Gboard > Permissions"* | Permission maangta hai |
| **Download gate** | *"Advanced voice typing features will be available once the language download is complete"* / *"Downloading stylus handwriting model"* / *"Downloading sign-to-text... %d%%"* | Model download zaroori |

## 0.4 ⛔ DISABLED-STATE reasons (item grey-out logic)

Gboard har disabled item par **exact reason** dikhata hai — yeh pattern MgBoard ko copy karna chahiye:

```
🟢 Disabled because empty editor
🟢 Disabled because items are selected.
🟢 Disabled because no text selected
🟢 Disabled because opt-in is disabled
🟢 Disabled because there is no enough room for the dialog.
🟢 Deactivated because the opt-in option is disabled
🟢 Command not available in this app
🟢 Can't show suggestions for this. Please try something else.
🟢 Can't find any suggestions. Check back as suggestions keep improving.
🟢 Current keyboard does not support resizing
```

## 0.5 Value-range validation logic

```
🟢 keyboard height ratio: "The value should between 0.5 and 2.0, inclusive"
🟢 access points count:   "Valid value should between 3 and 8, inclusive"
🟢 Morse timeout:         "How long Gboard waits before converting a Morse code sequence into a
                           letter. The default timeout is 1 second."
🟢 Haptic cutoff:         "Key release haptic feedback will not be performed if touch duration is
                           smaller than this given value (in milliseconds)."
```

## 0.6 Confirmation-before-destroy logic

```
🟢 "Your learned words will be erased. This operation cannot be undone.
    To confirm delete, enter the following number to continue."   ← NUMBER-ENTRY confirmation
🟢 "Clear all corrected words?"                                   ← dialog confirm
🟢 "Clear all on-device data that Gboard has saved to improve your
    typing and voice typing experience"
```

## 0.7 Consent / opt-in logic (3-link pattern)

```
🟢 <helpcenterlink>Learn more</helpcenterlink>
   <learningcenterlink>See voice commands</learningcenterlink>
   <personalizationlink>Manage personalization</personalizationlink>
🟢 "By using sign-to-text, you agree to Google's Terms of Service, AI Prohibited Use Policy,
    and Privacy Policy."
🟢 "Generative AI Terms of Service" (Proofread/Writing Tools ke liye)
🟢 "When you use this feature, your text, audio input and personal dictionary will be
    temporarily processed by Google. Rambler can make mistakes, so double-check it."
```

## 0.8 Onboarding / tooltip logic

```
🟢 "Educational tip about long-pressing the microphone to start voice dictation in locked mode."
🟢 "Educational tip about dictation in multiple languages."
🟢 "Remove the emoji fast-access row. Not clickable until the introduction tooltip is dismissed."
🟣 layout_promo_display_count / layout_promo_last_display_timestamp / layout_promo_result
🟣 voice_toolbar_onboarding_view_count / _click_count
🟣 pref_key_jarvis_opt_in_shown / pref_key_contacts_suggestion_notice_posted
🟢 "Autofill suggestions are now above your keyboard"
🟢 "Auto-fill suggestions are now above your keyboard"
```

---

# PART 1 — `Preferences` page ka FUNCTION + LOGIC

| Setting | Function (kya karta hai) | Logic / rule | Evidence |
|---|---|---|---|
| **Toolbar** (`show_toolbar`) | Toolbar (suggestion strip) ko on/off | OFF → suggestions strip gayab, keyboard body upar aa jaata hai. `opt_out_from_toolbar` alag key hai = user ne explicitly band kiya | 🟣 |
| **Show the keyboard toolbar while typing** | Typing shuru hone par toolbar dikhaye ya suggestions chhupa de | "Minimize toolbar and hide suggestions" iska opposite mode | 🟢 |
| **Suggestion strip** | Suggestions ka master switch | *"Manage when your keyboard shows suggestions"*; OFF → sirf access points bache ya strip poora off | 🟢 |
| **Show suggestions and access features** | Strip ka dual role | *"The keyboard toolbar shows suggestions and provides access to features."* | 🟢 |
| **Icons count on suggestions strip** | Kitne access point icons dikhein | *"By default, the suggestions strip shows a maximum of 5 (or 6 on landscape mode) access point icons. Valid value should between 3 and 8, inclusive"* | 🟢 |
| **Access point order** | Icons ka sequence | *"Define the order of displayed access point icons, separated by semicolon"* → `access_points_showing_order` | 🟢 |
| **Number row** (`enable_number_row`) | Letters ke upar 0-9 ki permanent row | Sub-rule: *"Always show on QWERTY, QWERTZ, and AZERTY layouts"* + separate *"…for password input"* (`enable_number_row_in_password`) | 🟢🟣 |
| **Key borders** (`enable_key_border`) | Keys ke around outline | Theme ke `.pill-shaped-if-bordered` variants isi par depend karte hain | 🟢🟣 |
| **Vibrate on keypress** (`enable_vibrate_on_keypress`) | Har key tap par haptic | `system_haptic_settings` se system-level haptics bhi; aliases: *"Haptic feedback, Key tap vibration, Haptics"* | 🟢🟣 |
| **Sound on keypress** (`enable_sound_on_keypress`) | Key click sound | `sound_volume` se volume control | 🟣 |
| **Popup on keypress** (`enable_popup_on_keypress`) | Key dabane par bada preview | Aliases: *"Character popup, Letter preview, Key pop-up"*; theme `.icon.for-popup-item.always-pressed-popup-item` | 🟢🟣 |
| **Key long-press delay** | Long-press trigger hone ka time | Aliases: *"Touch hold duration, Key press delay, Symbol delay"* | 🔵 |
| **Keyboard height** | Keyboard ki vertical size | **Do tarike:** (a) fixed mm — `keyboard_height_{33,35,37,39,47,48,49,52}_mm`; (b) ratio 0.5–2.0 — `keyboard_height_ratio`. **Rule:** mm set ho to ratio ignore; ratio set ho to mm ignore. Normal-mode-only variants alag (`normal_mode_keyboard_bottom_gap_*` se related) | 🟢🟣 |
| **Mini keyboard height** (`enable_mini_keyboard_height`) | Chhota keyboard variant | `Mini keyboard height` | 🟢🟣 |
| **Double tap on spacebar to add period followed by a space** | Space 2x tap → ". " | `enable_double_space_period`; aliases *"Double tap period, Spacebar period"* | 🟢🟣 |
| **Auto-capitalization** | Sentence ka pehla word capital | 🟢 verbatim: *"Capitalize the first word of each sentence while using on-screen keyboard"* + **alag** *"Capitalize the first word of each sentence while using physical keyboard"* → 2 independent toggles | 🟢 |
| **Show a dedicated key to switch languages** | Language-switch key | ⚠️ **Side-effect:** *"This hides the emoji key."* | 🟢 |
| **Show a dedicated key to access emoji** | Emoji key | ⚠️ **Side-effect:** *"This hides the language switch key."* / *"Access emoji key with a dedicated key. This hides the language switch key."* | 🟢 |
| **Show a button on the keyboard toolbar to open emoji keyboard** | Toolbar par emoji button | ⚠️ *"Only available in unfolded device state."* → foldable gate | 🟢 |
| **Show row of recently used emoji** | Emoji fast-access row | Removal: *"Remove the emoji fast-access row. Not clickable until the introduction tooltip is dismissed."* → tooltip-gated | 🟢 |
| **Emoji size** (`keyboard_emoji_scale_setting`) | Emoji ka scale | HI: *"इमोजी का साइज़, सिस्टम सेटिंग के हिसाब से तय होता है."* | 🟢🟣 |
| **Font size** (`keyboard_font_size_setting`) | Key labels ka font | *"Use device font size"* / *"Use system font instead of Gboard default one."* | 🟢🟣 |
| **One-handed mode** | Keyboard ek side shrink | *"One-handed mode shrinks down your keyboard to let you easily type with one hand"*; foldable variant `keyboard_mode_foldable_one_handed_mode`; exit = `Exit one-handed mode` | 🟢🟣 |
| **Split keyboard** | Do hisson mein banta keyboard (tablet) | ⚠️ *"When the keyboard is set to split layout, some keys will be duplicated on both sides"*; `Default input area width of split keyboard (in dp)` | 🟢 |
| **Floating keyboard** | Movable window keyboard | *"Enable floating keyboard automatically by default when the device is in landscape mode."* (`enable_auto_float_keyboard_in_landscape_mode`); resize ke 5 keys: `custom_body_height_ratio`, `custom_body_size`, `left_margin_ratio`, `padding_bottom`, `width_ratio`; exit = *"Drag here to exit floating mode"* / `Exit floating keyboard`; gesture = *"Pinch to scale, drag to move"* | 🟢🟣 |
| **Resize** | Keyboard height/position adjust | *"Change the size of the keyboard in the top-left corner"* variants (4 corners); gate: *"Current keyboard does not support resizing"* | 🟢 |
| **Multilingual typing** | Ek saath kai languages | ⚠️ **Limit logic:** *"At most %d language(s) can be enabled in multilingual typing"*; gate: *"Only applicable if multiple languages are enabled"* | 🟢 |
| **Show in app list** | Launcher mein Gboard icon | Aliases: *"App icon, Gboard icon, Hide icon"* | 🔵 |
| **Show physical keyboard toolbar** | PK connected ho to toolbar | *"Show the physical keyboard toolbar while typing."*; `show_pk_toolbar` | 🟢🟣 |
| **Autocorrect suggestions at cursor** | Toolbar minimize ho to suggestions cursor par | *"Autocorrect suggestions shown at the cursor when toolbar is minimized"*; `enable_at_cursor_suggestions_for_pk` | 🟢🟣 |

---

# PART 2 — `Theme` page ka FUNCTION + LOGIC

| Item | Function / Logic | Evidence |
|---|---|---|
| Theme picker | Keyboard ka colour scheme change; named themes (e.g. `Cyan Theme`) + `Default Gboard`, `Default dark theme`, `Default auto theme`, `Default system` | 🟢 |
| **Create keyboard theme with my image** | Apni photo se custom theme banata hai | 🟢 |
| **Switch to dark theme in battery saver** | Battery-saver mode ON hote hi dark theme force | 🟢 + 🟣 `enable_battery_saver_theme` |
| Dark theme rationale | *"Gboard can save power on OLED screens by going dark"* | 🟢 |
| Default theme files | `Default theme file name`, `Default dark theme file name`, `Default themes directory`, `additional_keyboard_theme` | 🟢🟣 |
| Dynamic color / Material You | System wallpaper se colors; theme selectors `m3_*` + `vibrant_m3_*` (2 variants = normal + vibrant) | 🟣 |
| Extra-small screen theme | *"Whether to enable the theme for extra small screen. This theme won't change the keyboard height."* → **theme height ko affect nahi karta** | 🟢 |
| Field gate | *"Theme settings are not available in this input field"* | 🟢 |
| Feature-menu link | *"Whether to show the Theme icon in the feature menu."* | 🟣 |
| Keytop variants | Theme keys height/border/mode par depend: `.keytop.dark.for-numpad.pill-shaped-if-bordered`, `.pill-shaped.pill-colored.extra-padding`, `.2rows-key`, `.for_morse_keyboard`, `.for-space-bar.vertical.2rows-key` | 🟢 |

---

# PART 3 — `Languages` page ka FUNCTION + LOGIC

| Item | Function / Logic | Evidence |
|---|---|---|
| **Add keyboard** | Nayi language/layout add; flow = language search → layout choose | 🟢 |
| **Disable languages / layouts** | Enable ki hui languages hatana | 🟢 |
| `language_settings_key`, `enabled_ime_language_tags` | Enabled languages ka persisted set | 🟣 |
| **Enable these languages that were shared with you** | Doosre user se shared language pack enable | 🟢 |
| Multilingual limit | *"At most %d language(s) can be enabled in multilingual typing"* | 🟢 |
| **Layouts** | Har language ke multiple layouts; examples: `Bulgarian, Phonetic (Traditional)`, `Chakma, compact`, `Cree (Syllabics)`, `Cree (Latin)` | 🟢 |
| Default layouts | `Default layouts for languages` | 🟢 |
| Layout promo | `layout_promo_display_count/_last_display_timestamp/_result` → kitni baar promo dikhaya | 🟣 |
| Japanese-specific | *"Use QWERTY layout for alphabet input mode."*, *"Use QWERTY layout when device orientation is landscape."*, *"Add dedicated Digit keyboard in addition to Hiragana and Alphabet keyboard."*, `japanese_space_character_form` | 🟢🟣 |
| Chinese-specific | *"Use the shift key to toggle between Chinese and English"*, *"Enable the Shift key to toggle Chinese and English by default"*, inline vs floating composition: *"Control whether Chinese (Simplified) characters are composed in the inline text field or in a separate floating view."* (Cantonese/Traditional bhi) | 🟢 |
| Korean | `enable_incremental_gesture_input_ko` | 🟣 |
| **Show canonical romanization** | Romanized form dikhana | 🟢 |
| **Use half-width space** | CJK spacing | 🟢 |
| Field gate | *"Gboard settings are not available in this input field"* | 🟢 |

---

# PART 4 — `Glide typing` page ka FUNCTION + LOGIC

| Item | Function / Logic | Evidence |
|---|---|---|
| **Glide typing** (`enable_gesture_input`) | Finger ko letters par slide karke word | *"Glide your finger from letter to letter"* | 🟢🟣 |
| **Incremental gesture input** | Gesture ke dauraan hi live recognition | `enable_incremental_gesture_input` (+ `_ko`, `_zh_tw`); HI: *"अगले ग्लाइड इनपुट से पहले वर्तमान लेखन लेख को अपने आप प्रदर्शित करें"* | 🟢🟣 |
| **Auto-commit** | Agla glide shuru karne se pehle current text commit | 🟢 *"Auto commit the current composing text before the next glide input"* + `enable_gesture_auto_commit` | 🟢🟣 |
| **Show the suggested word while gesturing** | Gesture ke dauraan word dikhana | 🟢 |
| **Trail** | Finger ka path dikhana | 🟢 *"Show how your finger moves across the keyboard"* |
| **Cursor control by glide** | Spacebar par slide → cursor move | 🟢 *"Press & hold the space bar to move the cursor"*; aliases *"Gesture cursor, Slide spacebar, Move cursor"*; **fallback:** *"When disabled, use swipe on space bar for trackpad"* | 🟢🔵 |
| **Free cursor by long-press space** | Space long-press → trackpad mode | 🟣 `enable_free_cursor_by_long_press_space`; 🟢 *"Hold here for 3 seconds to lock cursor mode"* |
| **Delete swipe** | Backspace se left glide → word delete | 🟢 aliases *"Gesture delete, Slide delete, Swipe delete"*; HI *"ग्लाइड करके शब्द मिटाने की सुविधा"* |
| Cursor keys | `Cursor up/down/left/right` + *"Move the cursor to the beginning/end"* | 🟢 |
| **Autocorrect undo with backspace** | Backspace se auto-correction revert | 🔵 `Autocorrectie ongedaan maken met backspace` (NL) + 🟣 `pref_key_latin_enable_ac_revert`, `pref_key_jarvis_by_word_revert` |

---

# PART 5 — `Text correction` page ka FUNCTION + LOGIC

| Item | Function / Logic | Evidence |
|---|---|---|
| **Auto-correction** | Typos auto-fix | 🟢 *"Correct typos, grammar, and punctuation with just a tap. See suggestions after you type a message."* |
| **Auto-correction levels** | Kitna aggressive correction ho | 🟣 `pref_key_auto_correction_level_words_and_sentences`, `pref_key_latin_auto_correction_levels`, `pref_key_auto_correction_levels_auto_correction`; 🟢 *"Advanced correction configurations"* |
| **Use auto-correction** | Master toggle | 🟢 |
| **Auto-correction on physical keyboard** | PK ke liye ALAG toggle | 🟢 *"Auto-correct words while typing on physical keyboard."* + `pref_key_pk_auto_correction` |
| **Auto-capitalization** (on-screen) | Sentence-first word capital | 🟢 *"Capitalize the first word of each sentence while using on-screen keyboard"* |
| **Auto-capitalization on physical keyboard** | PK ke liye ALAG | 🟢 *"Capitalize the first word of each sentence while using physical keyboard"* + `latin_pk_auto_capitalize` |
| **Next-word suggestions** | Aage ka word predict | 🟢 + *"Automatically display word suggestions while you type with:"* (source选择) |
| **Use previous context to make suggestions** | Pichhle text ko context banana | 🟢 |
| **Automatically add a space after selecting a suggested word** | Suggestion select → trailing space | 🟢 |
| **Automatically switch back to the letter keyboard after an apostrophe (')** | Apostrophe ke baad layout revert | 🟢 |
| **Smart punctuation / auto-space** | Punctuation ke baad space | 🟣 `enable_auto_space_smart_punctuation`, `enable_autospace_after_punctuation`, `enable_auto_space_zh_hk/_zh_tw` |
| **Inline suggestion** | Composing text ke andar hi suggestion | 🟣 `pref_key_enable_inline_suggestion`; 🟢 *"Show typing text inline before selecting candidates"* |
| **Grammar checker** | Grammar errors | 🟣 `pref_key_enable_grammar_checker`; 🟢 *"Show the button that lets you correct grammar, spelling and punctuation mistakes"* (IT variant) |
| **Smart Compose** | Sentence completion | 🟢 |
| **Post-correction tracking** | Correction ke baad user ne undo kiya ya nahi | 🟣 `pref_key_post_correction_trigger_times`, `_undo_times`, `_undo_or_edit_times` |
| **Clear all corrected words** | Sikhe hue corrections reset | 🟢 + confirm dialog *"Clear all corrected words?"* |
| **Use dictionary words** | Dictionary-based suggestions | 🟢 |
| **Dynamic diacritic key** | Letter long-press par accent | 🟢 *"Enable dynamic diacritic key"*; PK variant: *"Control whether to add a diacritical mark or accent mark by touching & holding a letter key on physical keyboard."* |
| **Single-character candidates** | 1-char suggestions on/off | 🔵 `Enkelkarakterkandidate, aan/af` |
| **Rambler** (voice + AI correction) | 🟢 *"When you use this feature, your text, audio input and personal dictionary will be temporarily processed by Google. Rambler can make mistakes, so double-check it."* + *"Help improve Rambler by reporting quality issues or bugs"* |
| **Fast typing consent** | 🟢 (IT) *"Consent to fast typing to enable greater sentence-level correction"* |

---

# PART 6 — `Voice typing` page ka FUNCTION + LOGIC

| Item | Function / Logic | Evidence |
|---|---|---|
| **Voice typing** (`enable_voice_input`) | Mic se dictation | 🟢 permission gate: *"Gboard needs access to the microphone in order to enable voice typing."* |
| **Automatically start voice typing when keyboard is shown** | Keyboard khulte hi mic start | 🟢 + variant *"Enable voice toolbar and start voice typing automatically when keyboard is shown"* |
| **Double-tap mic = locked mode** | Continuous dictation | 🟢 *"Double-tap the mic to continue voice typing until you tap it again, close the keyboard or say 'Stop'"*; 🟢 *"Educational tip about long-pressing the microphone to start voice dictation in locked mode."* |
| **Offline speech recognition** | Bina internet dictation | 🟢 *"Offline mode lets you use voice typing even when there's no internet connection, but you may get slightly different text."*; model size 🟢 *"Faster Voice Typing has been installed (**100 MB**) and now works offline. You can deactivate this from Settings>Voice Typing."*; aliases *"Faster voice typing, Offline dictation, Local speech"*; 🟣 `enable_ondevice_voice` |
| **Download gate** | Language pack download zaroori | 🟢 *"Advanced voice typing features will be available once the language download is complete"* |
| **Auto language switching** | Boli hui language detect | 🟢 *"Adjust to the language you're speaking if it's supported and detected by advanced voice typing features"*; 🟢 multilingual condition: *"It works if you have previously set up and used at least 2 languages that support advanced voice typing features"*; 🟣 `enable_enhanced_voice_typing_automatic_language_switching`, `enable_auto_language_switching`, `enhanced_voice_typing_prefer_detect_language` |
| **Enhanced voice typing** | Better accuracy engine (NGA) | 🟣 `enable_enhanced_voice_typing` + `_auto_punctuation` + `_speech_enhancement`; 🔵 *"Assistant voice typing, NGA, Smart dictation"* |
| **Add punctuation automatically when voice typing** | Auto punctuation | 🟢 |
| **Voice commands** | Bolkar control | 🟢 *"All voice commands"*, *"Show voice commands"*, *"See voice commands and access settings."*; edit commands: *"Change the spelling of a word by saying it one letter at a time"*, *"Use your voice to make any edits"* |
| **Voice toolbar** | Pill/widget mode | 🟢 *"Use the toolbar for easy voice typing"*, *"Hold and drag to move toolbar"*, *"Force the toolbar in horizontal mode and disable dragging"*, *"To quickly find the toolbar, tap Minimize on your keyboard while voice typing"*, *"Hide your keyboard while voice typing"*; 🟣 `has_shown_voice_toolbar`, `last_voice_toolbar_dictate_time` |
| **Voice widget** | 🟢 *"Enable the voice widget"* | |
| **Contacts access for accuracy** | 🟢 *"Allowing access to contacts along with your microphone will make dictations more precise."* + *"Improve voice typing with more accurate name spelling?"* | |
| **Default input method** | 🟢 *"Activate voice typing as default text input method?"* / *"Dictation is now your default input method"* | |
| **Sign-to-text** | Handwriting-sign style voice? (separate) | 🟢 *"Always set Sign-to-text as default"*, `Hide sign-to-text` |
| **Dedicated dictation physical key** | 🟢 *"The key code to be fired when the user presses the dedicated dictation physical key on a physical keyboard."* | |
| **Global direct-to-dictation** | 🟣 `enable_global_direct_to_dictation` — *"Open the keyboard at any time"* / *"Open the keyboard anytime"* | 🟢 |
| **Dictation type** | 🟣 `dictation_type_jetson` (new engine) vs `dictation_type_traditional` | |
| **Debug** | 🟣 `dictation_debug_audio_dump`, `dictation_share_debug_audio` | |
| **Incognito gate** | 🟢 *"Voice typing is disabled in Incognito Mode"* | |
| **Field gate** | 🟢 *"Seamless voice typing is not available in this input field"*; *"Voice Typing is currently not available in %s"* (language) | |
| **Additional module** | 🟢 *"Additional module (Dictation)"* — `dictation_feature_split.apk` on-demand split | 🟣 `on_demand_feature_split` |

---

# PART 7 — `Emojis, Stickers & GIFs` page ka FUNCTION + LOGIC

| Item | Function / Logic | Evidence |
|---|---|---|
| **Emoji suggestions** | Type karte waqt emoji suggest | 🟢 *"Adds or replaces some words with emoji"*; 🔵 aliases *"Emoji suggestions, Text to emoji"*; 🟣 `pref_key_enable_emoji_suggestion` |
| **Emojify** | Text ko emoji mein badalna | 🟣 `enable_emojify` |
| **Emoji → Expression** | Emoji panel ka naya "Expression" form | 🟣 `enable_emoji_to_expression` |
| **Emoji fast-access row** | Recently used emoji ki row | 🟢 removal: *"Remove the emoji fast-access row. Not clickable until the introduction tooltip is dismissed."*; 🔵 *"Emoji bar, Quick emoji row"*; theme `.fast-access-bar-primary` |
| **Dedicated emoji key** | 🟢 *"Access emoji key with a dedicated key. This hides the language switch key."* ← **mutual exclusion** | |
| **Sticker suggestions** | Typing par sticker suggest | 🟢 + 🟣 `enable_sticker_predictions_while_typing` |
| **Show stickers and explore options on emoji key tap** | Emoji key tap → stickers + options | 🟢 |
| **GIF topics** | 🟢 *"Show topics next to GIF search bar"*; `gif_search_result` cache; *"You haven't used any GIFs yet"* | 🟢🟣 |
| **Emoji Kitchen** | 2 emoji mix → sticker | 🔵 *"Emoji Kitchen, Mix emoji, Combine emoji"*; 🟢 *"Emoji Kitchen sticker for %1$s and %2$s emoji"*; *"Search emoji kitchen"* |
| **Custom stickers** | Apne sticker packs | 🟢 `Custom stickers`, `Add sticker pack`, `Add pack`, `Created sticker`, `Delete selected stickers`, `Clear sticker deletion selections` |
| **Search** | 🟢 *"Search emojis, GIFs and more"*, `emoji_search_result`; *"Still loading emoji search data. Try again later."* | 🟢🟣 |
| **Share timestamps** | 🟣 `latest_emoji_share_from_emoji_kb_timestamp`, `latest_gif_…`, `latest_sticker_…` | |
| **Empty states** | 🟢 *"You haven't used any emojis yet"* / *"You haven't used any GIFs yet"* | |

---

# PART 8 — `Clipboard` page ka FUNCTION + LOGIC

| Item | Function / Logic | Evidence |
|---|---|---|
| **Clipboard** master | Copy/paste history | 🟢 *"Clipboard allows you to access your recent copy and paste history"* |
| **RETENTION = 1 HOUR** | ⚠️ Sabse important logic | 🟢 *"Gboard's clipboard lets you copy text and images, **keeping them for one hour**, to quickly paste many things at once."* |
| **Pin logic** | Pinned items permanent, unpinned 1 ghante baad delete | 🟢 *"Touch and hold a clip to pin it. **Unpinned clips will be deleted after 1 hour.**"*; 🟣 `clipboard_pinned_item_char_number`, `_word_number`, `_daily_log`, `clipboard_unpinned_item_threshold_time` |
| **Edit mode** | 🟢 *"Use the edit icon to pin, add or delete clips."* | |
| **Opt-in** | Pehli baar consent dialog | 🟣 `clipboard_opt_in`, `clipboard_opt_in_dialog_shown` |
| **Screenshots in clipboard** | Screenshots auto-add | 🟣 `enable_screenshot_in_clipboard`; 🟢 *"Add screenshots to your Gboard clipboard for easier pasting. You can change it anytime in Settings."* |
| **Screenshot permission gate** | 🟢 *"Clipboard screenshot feature requires full access to your images. Update the permission in System settings > Apps > Gboard > Permissions"* | |
| **Suggestions-bar integration** | Clipboard chips strip par | 🟢 *"Show recently copied text and images in suggestions bar"*; 🔵 *"Copy paste chips, Clipboard suggestions, Paste from clipboard"* |
| **Detail view** | 🟢 *"Show detailed information for a clip item."* / *"Hide detailed information for the clip item."* | |
| **App paste gate** | 🟢 *"%1$s doesn't allow pasting images here"* / *"The app doesn't support the handwriting gesture here"* | |
| **Search** | 🟢 `Search clipboard` | |
| **Empty state** | 🟢 *"Clipboard is empty"* / *"Once you copy a piece of text, it will show up here."* / *"Welcome to the Gboard clipboard. Any text you copy will be saved here."* | |
| **Retention config** | 🟣 `clipboard_history_retention`, `clipboard_add_entry`, `clipboard_first_shown_time`, `clipboard_latest_shown_time`, `clipboard_last_clicked_chip_timestamp` | |

---

# PART 9 — `Personalization` page ka FUNCTION + LOGIC

| Item | Function / Logic | Evidence |
|---|---|---|
| **Learned words** | Gboard jo words seekhta hai | 🟢 *"Learned words"*, *"Clearing learned words…"* |
| **Delete learned words and data** | Sab kuch reset | 🟢 **confirm logic:** *"Your learned words will be erased. This operation cannot be undone. **To confirm delete, enter the following number to continue.**"* |
| **Clear all on-device data** | 🟢 *"Clear all on-device data that Gboard has saved to improve your typing and voice typing experience"* | |
| **Adapt to usage** | 🟢 *"Adapt Gboard to your typing and voice typing usage patterns"*; consent: *"Improve typing and voice typing based on your Gboard usage patterns and corrections. Transcripts of what you say and type will be saved on this device. You can delete them anytime by deleting learned words and data."* | 🟢 |
| **Personalized dictionaries** | 🟣 `pref_key_use_personalized_dicts` | |
| **Personal dictionary** | Apne words + shortcuts | 🟢 *"You don't have any words in the personal dictionary. Tap '+' to add a word."*; `Add to dictionary`, `pref_key_enable_shortcuts_dictionary` |
| **Use conversation history as context** | Pichhli baatcheet ko context | 🟢 + 🟣 `pref_key_enable_conv2query` |
| **Use screen context** | Screen par kya hai usse context | 🟢 + 🟣 `pref_key_writing_tools_screen_context_opt_in*` (5 keys incl. timestamps + v1) |
| **Writing Tools opt-in** | 🟣 `pref_key_writing_tools_opt_in`, `_enable_pi_override`, `_pi_override_enabled_features`, `_v2_backend_type_override` | |
| **Proactive Assistance (Gemini)** | 🟢 *"With Proactive Assistance, Gemini shows you personalized suggestions right when you need them"* | |
| **Contacts suggestions** | 🟢 *"Access information from contacts for suggestions"*, *"Turn on contacts suggestions."*, `pref_key_contacts_suggestion_notice_posted`; gate: *"Double-check suggestions before sharing personal info"* | 🟢🟣 |
| **Customized order** | Feature order personalization | 🟣 `customized_order_personalize_last_check_time`, `_last_checked_feature` |
| **Jarvis** | Internal AI feature | 🟣 `pref_key_jarvis_eligible`, `_opt_in`, `_opt_in_shown`, `_by_word_revert` |
| **Manage personalization** | Consent hub | 🟢 `<personalizationlink>Manage personalization</personalizationlink>` |

---

# PART 10 — `Privacy` page ka FUNCTION + LOGIC

| Item | Function / Logic | Evidence |
|---|---|---|
| **Incognito mode** | Na seekhe, na save kare | 🟢 *"Voice typing is disabled in Incognito Mode"* — **side-effect:** voice typing auto-off |
| **Usage statistics** | 🟢 *"Automatically send keyboard usage statistics to Google"* | |
| **Voice donation (audio snippets)** | Audio Google ko bhejna | 🟢 *"If you agree, snippets of your audio input on Gboard will be sent to and stored by Google to help improve voice typing for everyone."*; revoke: *"You can remove permission to store new audio snippets on Google servers anytime by turning this feature off in keyboard settings. **You won't have access to your audio snippets once they're sent to Google.**"*; 🟣 `enable_voice_donation` |
| **Snippet limits** | 🟢 *"Audio snippets won't be associated with you or your account. Snippets contain a maximum of **15 seconds** of speech and can be up to **25 seconds** long if there are periods of silence or unrecognized speech. Google won't keep them longer than **18 months**."* | |
| **Crowdsource** | 🟢 *"Donate audio to help Google get better at %s"*, *"Record and donate simple phrases in %s with Google Crowdsource…"* | |
| **Dictated-text improvement** | 🟢 *"Changes you make to dictated text may be used to improve your use of voice typing features."* + banner | |
| **Location** | 🟢 `Location` | |
| **Profile separation** | 🟢 *"Each of your Gboard settings applies to both your personal profile and your work profile, but neither profile can access suggestions, clipboard content, dictionaries or recent emojis associated with the other profile"* | |
| **Work-profile gate** | 🟢 *"Advanced features are currently unavailable for apps in a work profile. Switch to your personal apps to use this."* | |
| **Permissions** | Microphone (voice typing) · Camera (*"Gboard needs access to the camera in order to enable Scan text."*) · Contacts (Quick Insert: *"Allow Quick Insert to access your contacts?"*) · Images (clipboard screenshots) | 🟢 |
| **Delete data aliases** | 🔵 *"Delete data, Clear history, Reset Gboard"* | |

---

# PART 11 — `Advanced` page ka FUNCTION + LOGIC

### 11.1 Physical keyboard (PK)

| Item | Logic | Evidence |
|---|---|---|
| Access keys and shortcuts without a physical keyboard | ⚠️ **Side-effect:** *"Turning on this feature will also add additional keys to some on-screen keyboard layouts **when your device is in tablet mode**."* | 🟢 |
| PK simulator | 🟢 *"Enables advanced modifier key handling (Shift, Ctrl, Alt, Meta) for PK simulator."* + `enable_pk_simulator_setting` | 🟢🟣 |
| Auto-correct on PK | 🟢 *"Auto-correct words while typing on physical keyboard."* (on-screen se ALAG toggle) | 🟢 |
| Auto-capitalization on PK | 🟢 *"Capitalize the first word of each sentence while using physical keyboard"* | 🟢 |
| Diacritics via long-press | 🟢 *"Control whether to add a diacritical mark or accent mark by touching & holding a letter key on physical keyboard."* | 🟢 |
| Kana vs Romaji | 🟢 *"Use Kana input instead of Romaji input on the physical keyboard."* | 🟢 |
| Language switch | 🟢 *"Press Shift+Space to switch language"* | 🟢 |
| PK toolbar | 🟢 *"Show the physical keyboard toolbar while typing."* + orientation tooltip `enable_always_show_pk_toolbar_orientation_tooltip` | 🟢🟣 |
| Undo handover | 🟢 *"When enabled, Gboard stops handling the CTRL+Z key sequence. Instead, the app will handle it."* | 🟢 |
| Field gates | 🟢 *"Cannot show keyboard shortcuts in this input field"*, *"Gboard sharing isn't supported for this input field"* | 🟢 |

### 11.2 Handwriting / Stylus

| Item | Logic | Evidence |
|---|---|---|
| Use stylus to write in text fields | 🟢 *"Use your stylus to write in any text field. Your handwriting will be converted to text that you can edit or delete."* | 🟢 |
| Commit immediately | 🟢 *"Commit handwritten text immediately"* / *"Convert words immediately while writing"* | 🟢 |
| Model download | 🟢 *"Downloading stylus handwriting model"*, *"Error creating handwriting model for %s"* | 🟢 |
| Tuning | 🟣 `handwriting_scrollout_delay`, `handwriting_stroke_width_scale`, `handwriting_timeout_ms` | 🟣 |
| Stylus gestures | 🟢 *"Draw a circle around a letter, word or phrase to select it"*, *"Draw down, then %s with your stylus to move text to a new line"*, *"Use your stylus to draw a caret or arrow where you want to add new text"*, *"Scratch out a letter, word or phrase to delete it"* | 🟢 |
| Half/full screen | 🟢 *"Switch to half-screen handwriting"* / *"Switch to full-screen handwriting"* | 🟢 |
| Sign-to-text | 🟢 *"Always set Sign-to-text as default"*, *"By using sign-to-text, you agree to Google's Terms of Service, AI Prohibited Use Policy, and Privacy Policy."*, *"Downloading sign-to-text... %d%%"* | 🟢 |
| Stylus toolbar | 🟣 `disable_stylus_toolbar`; menus *"Open/Close more stylus options list"* | 🟢🟣 |
| Field/app gates | 🟢 *"The app doesn't support the handwriting gesture here"*, *"Handwriting demo is not available in this input field"* | 🟢 |
| Feature-menu flag | 🟣 *"Whether to show the Handwriting icon in the feature menu."* | 🟣 |

### 11.3 Morse code

| Item | Logic | Evidence |
|---|---|---|
| Dot / Dash key assignment | 🟢 *"Control the Morse keyboard with external switches. This is where you assign switches to the dot key function"* (+ dash variant); 🟣 `pref_key_morse_dot_key_assignment`, `_dash_key_assignment`, `Clear key assignment` | 🟢🟣 |
| Character commit timeout | 🟢 *"This setting determines how long Gboard waits before converting a Morse code sequence into a letter. **The default timeout is 1 second.**"*; 🟣 `pref_key_latin_morse_character_commit_timeout`, `pref_key_morse_enable_character_commit`, `Enable character timeout` | 🟢🟣 |
| Word commit timeout | 🟣 `pref_key_latin_morse_word_commit_timeout`, `pref_key_morse_enable_word_commit`, `Enable word timeout` | 🟣 |
| Key repeat on hold | 🟣 `pref_key_morse_enable_key_repeat_on_hold`, `_repeat_interval`, `_repeat_start_delay`; 🟢 `Enable key repeat` | 🟢🟣 |
| Hint card | 🟢 `Show morse hint card` / `Hide morse hint card` | 🟢 |
| Theme keys | 🟢 `.keytop.for-bottom-key.for-action-key-holder.for_morse_keyboard`, `.keytop.dark.for-space-bar.for_morse_keyboard` | 🟢 |

### 11.4 Display / system

| Item | Logic | Evidence |
|---|---|---|
| Display cutout | 🟢 *"Enable this feature if you want to customize the Ignore display cutout area feature."* → `enable_display_cutout_customization`; **`padding-project` se directly related** | 🟢 |
| Display density | 🟢 *"Adjust the display density dpi for rendering keyboard."* | 🟢 |
| Keyboard height ratios (normal-mode-only) | 🟢 *"…in normal keyboard portrait mode. The value should between 0.5 and 2.0, inclusive."* + foldable-unfolded variants | 🟢 |
| Keyboard mode enforce | 🟢 *"The keyboard mode will be enforced to normal mode if the default value is set to 1 and the preference visibility is set to invisible… only works on phone devices."*; `Default keyboard mode on phone or tablet screen`, `Default keyboard mode on foldable unfolded screen` | 🟢 |
| Tri-state keyboard | 🟢 `Use tri-state keyboard` | 🟢 |
| Text preview | 🟢 `Enable the text preview` | 🟢 |
| Accessibility layout | 🟢 `Show accessibility layout` | 🟢 |
| Autofill | 🟢 `Autofill`; *"Autofill suggestions are now above your keyboard"* | 🟢 |
| Text editing | 🟢 `Text editing` — cursor arrows + select/copy/paste | 🟢 |
| CursorAnchorInfo | 🟣 `pref_key_enable_show_cursoranchorinfo` | 🟣 |
| App markup | 🟢 `Enable app markup` | 🟢 |
| Debug | 🟢 `Debug Feature Split`, `Create a new bug report`, `Open source licenses`, `Current version` | 🟢 |

---

# PART 12 — DEFAULTS jo APK se confirm hue (MgBoard ke liye ready values)

| Setting | Default value | Evidence |
|---|---|---|
| Toolbar access-point icons | **5 portrait / 6 landscape**, range 3–8 | 🟢 verbatim |
| Keyboard height | mm presets **33 / 35 / 37 / 39 / 47 / 48 / 49 / 52 mm** | 🟣 config keys |
| Keyboard height ratio | **1.0**, valid **0.5–2.0 inclusive** | 🟢 verbatim |
| Clipboard retention | **1 hour** (unpinned clips delete) | 🟢 verbatim |
| Morse character timeout | **1 second** | 🟢 verbatim |
| Voice snippets | max **15 s** speech, up to **25 s** with silence, kept **≤ 18 months** | 🟢 verbatim |
| Offline voice model | **100 MB** download | 🟢 verbatim |
| Haptic cutoff | touch duration < X **ms** → release haptic skip | 🟢 verbatim |
| Multilingual typing | **At most %d languages** (dynamic value) | 🟢 |
| Split keyboard | `Default input area width of split keyboard (in dp)` | 🟢 |
| Cursor lock | hold **3 seconds** to lock cursor mode | 🟢 |

---

# PART 13 — MgBoard implementation: LOGIC patterns (code-ready)

## 13.1 Setting item with full logic metadata

```kotlin
data class MgSettingItem(
    override val id: String,                    // "enable_double_space_period"
    override val labelEn: String,
    override val labelHi: String,
    override val summaryEn: String?,            // Gboard: har toggle ka summary hota hai
    override val summaryHi: String?,
    override val searchKeywords: List<String>,  // §6 alias pattern
    override val page: MgSettingsPage,

    // ---- LOGIC FIELDS (Gboard-exact) ----
    val default: Any? = null,                   // true / 1.0f / "37mm"
    val range: ClosedFloatingPointRange<Float>? = null,   // 0.5f..2.0f
    val mutuallyExclusiveWith: List<String> = emptyList(),// §0.2
    val overridesValueOf: String? = null,       // e.g. mm-height overrides ratio
    val overriddenBy: String? = null,
    val dependsOn: String? = null,              // parent toggle
    val visibleIf: Gate? = null,                // §0.3
    val disabledReasonEn: String? = null,       // §0.4 "Disabled because …"
    val disabledReasonHi: String? = null,
    val requiresPermission: String? = null,     // "RECORD_AUDIO" / "CAMERA" / "READ_CONTACTS"
    val requiresDownload: String? = null,       // "offline_voice_100mb"
    val confirmBeforeChange: Confirm? = null,   // §0.6 NUMBER_ENTRY / DIALOG
    val sideEffects: List<String> = emptyList(),// e.g. "hides_emoji_key"
    val onChange: ((Any?) -> Unit)? = null,     // live apply, no restart
)

sealed class Gate {
    object None : Gate()
    data class LanguageSupported(val feature: String) : Gate()
    data class DeviceState(val state: String) : Gate()      // "unfolded"
    data class Profile(val notWorkProfile: Boolean) : Gate()
    data class Incognito(val mustBeOff: Boolean) : Gate()
    data class InputField(val allowedTypes: Set<String>) : Gate()
    data class Orientation(val orientation: String) : Gate() // "landscape"
    data class Toggle(val parentId: String, val mustBe: Boolean) : Gate()
}

enum class Confirm { NONE, DIALOG, NUMBER_ENTRY }   // Gboard: learned-words = NUMBER_ENTRY
```

## 13.2 Mutual-exclusion engine (Gboard §0.2 logic)

```kotlin
fun applyMutualExclusion(changed: MgSettingItem, newValue: Boolean) {
    if (!newValue) return
    changed.mutuallyExclusiveWith.forEach { otherId ->
        val other = registry[otherId] as? ToggleItem ?: return@forEach
        prefs.edit().putBoolean(otherId, false).apply()      // auto-off
        other.onSideEffect?.invoke()                          // e.g. hide emoji key
    }
    // value-override logic (mm height vs ratio)
    changed.overridesValueOf?.let { prefs.edit().remove(it).apply() }
}
```

## 13.3 Gate evaluation (Gboard §0.3 logic)

```kotlin
fun evaluate(item: MgSettingItem, ctx: KeyboardContext): ItemState = when {
    !gateAllows(item.visibleIf, ctx)      -> ItemState.Hidden
    item.requiresPermission != null &&
        !ctx.hasPermission(item.requiresPermission!!) ->
            ItemState.Disabled(ctx.string(R.string.needs_permission, item.requiresPermission))
    item.requiresDownload != null &&
        !ctx.isModelReady(item.requiresDownload!!)   -> ItemState.Disabled(ctx.downloadLabel)
    ctx.incognito && item.id == "enable_voice_input" ->
            ItemState.Disabled("Voice typing is disabled in Incognito Mode")
    ctx.workProfile && item.needsPersonalProfile     ->
            ItemState.Disabled("Advanced features are unavailable in a work profile")
    !ctx.languageSupports(item.id)        -> ItemState.Disabled("Advanced features aren't available in your current Gboard language")
    else -> ItemState.Enabled
}
```

## 13.4 Confirm-before-destroy (Gboard §0.6)

```kotlin
// "Delete learned words and data" — Gboard NUMBER_ENTRY pattern
fun deleteLearnedWords() {
    val challenge = Random.nextInt(1000, 9999)
    showDialog(
        title  = "Delete learned words and data",
        body   = "Your learned words will be erased. This operation cannot be undone. " +
                 "To confirm delete, enter the following number to continue.",
        challenge = challenge,
        onConfirm = { if (it == challenge) clearAllLearnedData() }
    )
}
```

## 13.5 Live-apply (no restart) — Gboard behaviour

```kotlin
prefs.registerOnSharedPreferenceChangeListener { _, key ->
    when (key) {
        "enable_key_border"          -> keyboardView.invalidateKeyBackground()
        "keyboard_height_ratio",
        "keyboard_height_mm"         -> keyboardView.requestHeightRecalc()   // padding-project se link
        "enable_number_row"          -> layoutManager.rebuildRows()
        "show_toolbar"               -> toolbarController.setVisibility(prefs.toolbarEnabled)
        "access_points_showing_order"-> toolbarController.reorder(prefs.accessPointOrder)
        "enable_vibrate_on_keypress" -> hapticsController.reload()
        "theme_id",
        "enable_battery_saver_theme" -> themeController.reload()
    }
}
```

---

# PART 14 — Cross-project logic links

| Project | Setting logic jo usse judti hai |
|---|---|
| `padding-project` | `keyboard_height_*` (mm + ratio), `enable_display_cutout_customization`, "normal keyboard" height ratios, keyboard-mode enforce rule → **bottom gap = height model ka hissa** |
| `toolbar-project` | `show_toolbar`, `access_points_count_on_bar` (3–8, 5/6), `access_points_showing_order`, `enable_writing_tools_icon_in_suggestion_strip` |
| `grid-menu-project` | `Customize feature menus`, 3 "Whether to show the X icon in the feature menu." flags, drag-to-reorganize |
| `voice-pill-project` | Voice typing page ka **poora** section — auto-start, double-tap lock, toolbar drag, 100 MB offline model, voice commands |

---

# PART 15 — Honest gaps (functions/logic mein jo confirm nahi ho paya)

| Gap | Reason |
|---|---|
| Har toggle ka **boolean default** (true/false) | Defaults code/XML mein hain, arsc mein nahi. Sirf value-defaults (mm/ratio/timeout/retention) confirm hue |
| **Kaun si setting kis page mein** (exact XML hierarchy) | Gboard settings code/server-driven; page-assignment labels+flags+summaries se infer kiya |
| Auto-correction **levels** ki exact values | `pref_key_latin_auto_correction_levels` key mila, values nahi |
| Multilingual typing ka **exact %d** | Dynamic (device/language dependent) |
| Settings **icons/drawables** | Beta build obfuscated |
| `Sound on keypress` ka exact label wording | Flag confirmed, label version-specific |

---

*Sources: `/tmp/en3.txt` (65,703 strict-English lines from Gboard 18.3.1.977415014-beta arsc),
`/tmp/allflags.txt` (432 config names), `/tmp/gx/all_strings.txt` (220,375 all-language strings),
`/tmp/gx/reslist.txt` (8,914 resources + theme selectors).*
