# MgBoard — Gboard-style Voice Pill (Assistant Voice Typing Toolbar)
## Deep Research + Complete Analysis

> **Method:** सिर्फ़ articles नहीं — मैंने **असली Gboard APK download करके खोला**:
> `Gboard 18.3.1.977415014-beta-arm64-v8a` (versionCode 176004238, built 2026-09-14, minSdk 26, **targetSdk 37**),
> source: APKCombo → Cloudflare R2. उसके `AndroidManifest.xml` (binary AXML parse) और `resources.arsc`
> (**220,375 strings**) से सीधे सबूत निकाले हैं। इसके अलावा AOSP `InputMethodService.java`
> (android14-release) और 9to5Google / Android Authority के APK teardowns।
>
> कच्चा सबूत → `APK-EVIDENCE.md` · तुम्हारे spec से तुलना → `SPEC-vs-GBOARD.md`

---

## भाग 0 — TL;DR

1. Gboard इसे **"pill" नहीं कहता** — असली नाम है **voice toolbar**, और code-level पर **widget**
   (`widget_x_position`, `widget_change_widget_orientation`, `VOICE_enable_vertical_widget`).
2. **Gboard के पास `SYSTEM_ALERT_WINDOW` permission नहीं है** → pill कोई "display over other apps" overlay नहीं है।
3. pill असल में **वही IME window है** (collapsed state) — और बाकी स्क्रीन पर tap pass-through करने के लिए
   `InputMethodService.onComputeInsets()` के `TOUCHABLE_INSETS_*` का उपयोग होता है।
4. Drag असली है, position **persist** होता है (`widget_x_position` / `widget_y_position`),
   और orientation horizontal ↔ vertical दोनों तरफ़ से बदलता है (menu item **और** drag से)।
5. Recognition: **NGA** (Next Generation Assistant) + on-device dictation
   (`libdictation_jni.so` एक अलग feature-split में)।
6. Hindi strings APK में मौजूद हैं — और एक जगह तुम्हारा spec Gboard से **अलग** है:
   Gboard लिखता है **"अब बोलें"**, तुम्हारे spec में "बोल रहा हूं" है (देखो §8)।
7. तुम्हारे prompt का **§5 (system-wide floating overlay)** Gboard जैसा बनाना **बिना permission के असंभव** है —
   तीन विकल्प और मेरी सलाह §9 में।

---

## भाग 1 — Feature क्या है, कब आया, किस-किस को मिला

| समय | क्या हुआ |
|---|---|
| Jan 2024 | Pixel **Tablet** पर "Assistant voice typing toolbar" पहली बार (9to5Google teardown) |
| Jan 2024 | `seamless voice typing` flag मिला — "floating pill-shaped UI was used last" याद रखता है |
| Mar 2024 | Google ने इसे **March 2024 Pixel Feature Drop** में officially announce किया |
| Feb 2025 | Gboard **beta 15.0.03.717871796** पर Pixel phones में rollout; teardown में पूरा menu list मिला |
| Mar 2025 | सभी Android devices पर (tablets के बाद) |
| Apr 2025 | Pixel पर इसका नाम **"Advanced features"** हो गया; non-Pixel (Samsung वगैरह) पर वही toolbar, पर "Assistant" वाला हिस्सा नहीं |
| Sep 2026 | **18.3.1-beta** — यही version मैंने खोला; सब कुछ पक्का मौजूद है |

**Non-Pixel note (ज़रूरी):** 9to5Google — *"this toolbar is also available on non-Pixel phones, like Samsung
devices, but it's the regular voice typing experience instead of the 'Assistant' version exclusive to Google devices."*
यानी **UI/toolbar सबको मिलता है**, "Assistant"-specific smartness (voice commands, writing tools) Pixel-only है।
MgBoard के लिए अच्छी ख़बर: हमें जो चाहिए वो **UI हिस्सा** है, वो Pixel-exclusive नहीं।

---

## भाग 2 — सबसे बड़ा खुलासा: pill overlay नहीं, IME window ही है

### 2.1 Permissions का सबूत (Gboard 18.3.1-beta का पूरा manifest)

```
android.permission.ACCESS_NETWORK_STATE      android.permission.READ_USER_DICTIONARY
android.permission.ACCESS_WIFI_STATE         android.permission.RECEIVE_BOOT_COMPLETED
android.permission.FOREGROUND_SERVICE        android.permission.VIBRATE
android.permission.GET_ACCOUNTS              android.permission.WAKE_LOCK
android.permission.GET_PACKAGE_SIZE          android.permission.WRITE_USER_DICTIONARY
android.permission.INJECT_KEY_EVENTS         com.google.android.apps.aicore.service.BIND_SERVICE
android.permission.INTERNET                  com.google.android.providers.gsf.permission.READ_GSERVICES
android.permission.PERSONAL_CONTEXT_*  (3)   com.google.android.setupwizard.READ_DEVICE_ORIGIN_FIRST_PARTY
android.permission.READ_CONTACTS
```

**गौर से देखो — इनमें से कोई नहीं है:**
- ❌ `SYSTEM_ALERT_WINDOW` → "display over other apps" overlay **बिल्कुल नहीं**
- ❌ `RECORD_AUDIO` → Gboard खुद mic record नहीं करता; `SpeechRecognizer` **Google app के recognition
  service process** में चलता है, वहाँ से audio जाता है (इसलिए permission वहीं चाहिए)
- ❌ कोई `FOREGROUND_SERVICE_MICROPHONE` type service नहीं

> MgBoard के लिए नतीजा: अगर तुम `SpeechRecognizer` use करोगे तो तुम्हें भी `RECORD_AUDIO` declare करने की
> ज़रूरत नहीं (ग्राहक के रूप में) — पर Android 12+ पर mic privacy indicator तब भी दिखेगा।
> अगर अपना खुद का audio pipeline बनाओगे तो `RECORD_AUDIO` + (background के लिए) `FOREGROUND_SERVICE_MICROPHONE` चाहिए।

### 2.2 Manifest के सारे windows/services

```xml
<service android:name="com.android.inputmethod.latin.LatinIME"
         android:permission="android.permission.BIND_INPUT_METHOD"
         android:exported="true" android:directBootAware="true">   ← इकलौता IME
<service ... AndroidSpellCheckerService ... BIND_TEXT_SERVICE/>
<service ... SuperpacksForegroundTaskService  foregroundServiceType="0x800" (specialUse)/>  ← language-pack download
<service ... SuperpacksBackgroundJobService   BIND_JOB_SERVICE/>
<service ... PhenotypeMetadataHolderService   enabled=false/>      ← flags/experiments
<service ... InAppTrainingService             process=":train"/>
+ settings/launcher/first-run activities, clipboard provider, startup providers
```

**कोई overlay service नहीं, कोई floating-window service नहीं।** पूरा voice toolbar `LatinIME` के अंदर ही रहता है।

### 2.3 तो फिर वह "किसी भी app के ऊपर" कैसे तैरता है?

क्योंकि **IME window खुद एक system window है** (`TYPE_INPUT_METHOD`) जो हमेशा app windows के ऊपर रहता है —
और (पिछले padding-project के research से) हमने AOSP में verify किया था कि IME window की frame
**पूरी स्क्रीन तक** जाती है:

```java
// AOSP InputMethodService.onCreate()
lp.gravity = Gravity.BOTTOM;
lp.setFitInsetsTypes(statusBars() | navigationBars());
lp.setFitInsetsSides(Side.all() & ~Side.BOTTOM);   // bottom तक फैली window
window.setFlags(FLAG_LAYOUT_IN_SCREEN | FLAG_NOT_FOCUSABLE | FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS, ...);
```

यानी IME window का canvas पहले से ही पूरा स्क्रीन है। बस दो चीज़ें करनी हैं:
1. उसमें एक **छोटा-सा draggable view** (widget) रखो, बाकी सब **transparent**
2. बता दो कि window का **कौन-सा हिस्सा touchable है** — बाकी पर tap नीचे app को चला जाए

### 2.4 Touch pass-through का असली API

```java
// AOSP InputMethodService.Insets (line ~1370-1450)
public int contentTopInsets;    // main content कहाँ से शुरू — app को resize/pan करने के लिए
public int visibleTopInsets;    // कितना हिस्सा app को ढक रहा है (बार-बार बदल सकता है)
public final Region touchableRegion;
public int touchableInsets;     // FRAME | CONTENT | VISIBLE | REGION
```
- `TOUCHABLE_INSETS_FRAME` (default) → **पूरी window** touchable
- `TOUCHABLE_INSETS_CONTENT` → सिर्फ़ `contentInsets` के अंदर का हिस्सा touchable, बाकी pass-through
- `TOUCHABLE_INSETS_REGION` → तुम खुद `touchableRegion` (Region) सेट करो — **सबसे सटीक**

**यही वो trick है** जिससे pill स्क्रीन के बीच में कहीं भी तैर सकती है और बाकी स्क्रीन पर user
आराम से app चलाता रहता है — बिना किसी overlay permission के।

```java
@Override public void onComputeInsets(Insets outInsets) {
    super.onComputeInsets(outInsets);
    if (voiceWidgetMode && !fullKeyboardShown) {
        outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_REGION;
        outInsets.touchableRegion.set(widgetRectOnScreen);   // सिर्फ़ pill का box
        outInsets.contentTopInsets = outInsets.visibleTopInsets = decor.getHeight(); // app को resize मत करो
    }
}
```

> **Caveat:** `TOUCHABLE_INSETS_*` सिर्फ़ **touch** pass-through करता है। window का transparent हिस्सा
> visually pass-through होता है, पर `FLAG_NOT_TOUCH_MODAL` जैसा behavior चाहिए तो window flags भी देखने पड़ेंगे।
> और `FLAG_NOT_FOCUSABLE` (IME में पहले से है) की वजह से pill पर text-input focus नहीं जाएगा —
> इसलिए pill से typing नहीं, सिर्फ़ commands चलेंगी। यही design सही भी है।

### 2.5 pill की ज़िंदगी की असली हद (important reality)

IME window तब मरता है जब:
- user किसी non-input जगह tap करे / back दबाए → system IME hide कर देता है
- सारा input focus खत्म हो जाए

इसलिए pill **"हर app में हमेशा"** नहीं रह सकती। असल behavior यह है (9to5Google, verified):
> *"this toolbar is persistent and remains active **the next time you open a text field** until you manually bring back the keyboard."*

यानी Gboard **mode को prefs में याद रखता है** — अगली बार कोई text field खुला तो pill अपने-आप फिर दिख जाती है।
APK में इसी के सबूत:
```
has_shown_voice_toolbar        voice_toolbar_shown_count
last_voice_toolbar_dictate_time  last_voice_dictate_time
opt_out_from_voice_toolbar     opt_out_from_stt_toolbar
voice_toolbar_onboarding       seamless_voice_typing
"Enable voice toolbar and start voice typing automatically when keyboard is shown"
"Force the toolbar in horizontal mode and disable dragging"   ← dev flag
```

---

## भाग 3 — Gboard का असली architecture (APK strings से reverse-engineered)

```
┌──────────────────────────────────────────────────────────────────────┐
│  LatinIME (InputMethodService)  — एकमात्र window-owning component     │
│                                                                      │
│  ┌────────────────────────────────────────────────────────────────┐  │
│  │ Voice/Dictation layer                                          │  │
│  │  • engine: NGA (res/NGA.xml) + on-device libdictation_jni.so   │  │
│  │  • modes: dictation_type_traditional | dictation_type_jetson   │  │
│  │  • flags: enable_ondevice_voice, enable_enhanced_voice_typing, │  │
│  │           _auto_punctuation, _automatic_language_switching,    │  │
│  │           _speech_enhancement, seamless_voice_typing           │  │
│  └────────────────────────────────────────────────────────────────┘  │
│                              │ transcript                            │
│  ┌───────────────────────────▼────────────────────────────────────┐  │
│  │ "Widget"  = draggable toolbar (यही तुम्हारी pill है)            │  │
│  │  states : FULL_KEYBOARD ⇄ HORIZONTAL_WIDGET ⇄ VERTICAL_WIDGET  │  │
│  │  popup  : widget-popup-menu (access points + language list)    │  │
│  │  panels : symbols / emoji / clipboard / translate / writing    │  │
│  │  tooltip: widget-tooltip (+ 3 button variants)                 │  │
│  │  persist: widget_x_position, widget_y_position  (per-keyboard) │  │
│  │  render : Snygg-style theme elements (नीचे list)                │  │
│  └────────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────┘
```

### 3.1 Action IDs (बटन/मेनू आइटम के असली नाम)

```
widget_hide_keyboard              ← ⌨ keyboard icon → pill बंद, full keyboard वापस
widget_keyboard                   ← keyboard access point
widget_delete                     ← ⌫ backspace
widget_ime_action                 ← editor का action key (send/go/next…)
widget_change_widget_orientation  ← ⟲ horizontal ↔ vertical flip
widget_more_access_points         ← "…" और tools
widget_access_point_settings      ← ⚙ settings
widget_access_point_stylus_gestures
widget_enable_markup
```

### 3.2 Theme element names (Snygg-style) — यानी UI code से बनता है, XML layout से नहीं

```
.widget-keyboard.keyboard-background.horizontal      ← horizontal pill का background
.widget-keyboard.keyboard-background.vertical        ← vertical pill का background
.widget-keyboard.keyboard-body-area                  ← अंदर का content area
.widget-content-wrapper.with_background | .no-background | .entry-menu
.widget-popup-menu-item.non-linear-scale             ← menu का हर row
.widget-popup-menu-entry-label                       ← item का text
.widget-popup-menu-entry-end-icon.non-linear-scale   ← checkmark / trailing icon
.widget-popup-menu-entry-header-label                ← menu header
.widget-popup-menu-entry-shortcuts-key               ← shortcut hint
.widget-tooltip  / -icon / -label
.widget-tooltip-button.borderless | .neutral-with-border | .positive-with-border
.widget-item-background
.icon.on-widget-icon-background.item-ripple          ← icon का ripple
.keytop.for-candidate-key.for-widget
.label.secondary.text-size-very-tiny.for-widget      ← छोटा language tag जैसा label
.widget.proactive-suggestions-holder-border
+ res/color/m3_{standard,vibrant}_toolbar_{button_text,icon_button_container,
                                        icon_button_icon,icon_button_ripple}_color_selector.xml
```

**निष्कर्ष:** pill पूरी तरह **programmatically drawn** है (Gboard के अपने theme engine से), कोई `res/layout/*.xml` नहीं।
इसलिए APK में कोई layout file नहीं मिली — सिर्फ़ color selectors। MgBoard के लिए इसका मतलब:
XML layout से भी बना सकते हो, पर **background/ripple/theme colors को अलग रखो** ताकि horizontal↔vertical flip
सिर्फ़ layout-swap से हो जाए, पूरा re-inflate न करना पड़े।

---

## भाग 4 — 5 States का exact mapping (तुम्हारा spec ↔ Gboard का असली behavior)

### State 1 — Full Voice Panel
**तुम्हारा spec:** mic दबाने पर खुले; "बोल रहा हूं / Speak now" + नीचे text-keyboard + ऊपर-दाएँ ˅˅ + भाषा badge + नीला mic circle

**Gboard में असल में:**
- mic दबाने पर **full keyboard खुला रहता है**, उसके ऊपर एक **voice toolbar strip** आ जाता है
  (Android Authority: *"a bar at the top of the keyboard … microphone button featuring Google's colors to the right …
  buttons to minimize the keyboard, return to the previous toolbar page, and view supported voice commands"*)
- बीच में status: **"Speak now"** / **"Paused"** / **"Listening…"**, और बोलना रुकने पर **voice-command suggestions**
- ˅˅ (double chevron) = **"Collapse keyboard"** / **"Minimize toolbar and hide suggestions"** / **"Minimize keyboard"**
  — Hindi: *"टूलबार को छोटा करें और सुझावों को छिपाएं"*
- onboarding: *"To quickly find the toolbar, tap MINIMIZE minimize icon on your keyboard while voice typing"*

> ⚠️ फ़र्क़: Gboard में panel खुलने पर **keyboard नीचे बना रहता है** (तुम्हारे spec में भी यही लिखा है — ठीक है),
> पर Gboard का status text **"Speak now"/"अब बोलें"** है, "बोल रहा हूं" नहीं।

### State 2 — Horizontal Pill (यही असली "voice toolbar")
**तुम्हारा spec order:** `भाषा-badge` — `⌨` — `status-text` — `⌫` — `🎤` (mic के कोने पर छोटा भाषा-tag)

**Gboard का order (teardown + strings):**
- बाएँ: **hamburger / language access point** → menu खोलता है (*"A hamburger button at the left lets you access…"*)
- बीच: **status text** — "Speak now" / "Paused" / voice-command suggestions
- दाएँ: **⌫ backspace** फिर **🎤 mic** (*"The mic remains at the right with a delete key next to it"*)
- **⌨ keyboard icon** — *"Quickly switch from the toolbar to the keyboard. Just tap the keyboard icon."*
  (Android Authority: minimize करने पर *"the toolbar replaces the minimize button with a backspace key and the 'i'
  button with a shortcut to reopen the full keyboard layout"*)
- छोटा language tag: APK में `.label.secondary.text-size-very-tiny.for-widget` element मिला है — यही वो छोटा label है

**Sticky behavior:** *"Notably, this toolbar is persistent and remains active the next time you open a text field
until you manually bring back the keyboard."* → pause पर pill गायब नहीं होती, सिर्फ़ status बदलता है ✔ (तुम्हारा spec सही)

### State 3 — Menu Open
**तुम्हारा spec:** ऊपर की ओर खुले; सेटिंग्स, क्लिपबोर्ड, अनुवाद, इमोजी, सिंबल-कीबोर्ड, वर्टिकल टूलबार; divider के बाद भाषा-list

**APK से मिला exact menu (9to5Google teardown से भी match):**
```
Settings                       →  सेटिंग
Show voice commands            →  (तुम्हारे spec में नहीं है!)
Show clipboard                 →  क्लिपबोर्ड दिखाएं
Show translate                 →  अनुवाद दिखाएं
Show emoji                     →  इमोजी दिखाएं
Switch to vertical toolbar     →  वर्टिकल टूलबार पर स्विच करें
Feedback                       →  (तुम्हारे spec में नहीं है)
```
+ *"All voice commands"*, *"Close voice command list"*, *"Hide voice commands"*

**Menu का असली नाम:** `widget-popup-menu` — element names: `-entry-label`, `-entry-end-icon` (checkmark),
`-entry-header-label`, `-item.non-linear-scale`।
Accessibility labels: **"Open more voice options" / "More voice options opened" / "More voice options closed"**
(Hindi: *"अन्य वॉइस विकल्प खोलें"* वगैरह)।

**"Menu खुलते ही mic auto-pause"** → Gboard में यह behavior मौजूद है (menu एक popup है जो focus लेता है;
recognition pause हो जाता है)। MgBoard में इसे explicitly implement करना पड़ेगा — APK में इसका कोई
अलग string नहीं मिला, यानी यह implicit behavior है।

### State 4 — Symbols Overlay
**तुम्हारा spec:** "चिह्न" header + X + symbol-grid + नीचे category tabs (history, 1?#, brackets, arrow, math, numbered-list)

**APK में exact यही categories मिलीं:**
```
Recent  ·  Numbers  ·  Brackets  ·  Arrows  ·  Mathematics  ·  List      + SYMBOLS / Symbols / Symbols mode
Hindi: "चिह्न"  (29 जगह)
Close: "Close button", "Close menu", "Hide symbol keyboard", "Hide symbols keyboard", "Symbol keyboard"
```
→ तुम्हारा spec हूबहू सही है ✔

### State 5 — Vertical Pill
**तुम्हारा spec order (ऊपर→नीचे):** `🎤` → `⌫` → `⌨` → `भाषा-badge`
**तुम्हारा trigger:** pill को left/right किनारे तक drag करो → auto-flip; वापस center की ओर खींचो → auto-horizontal

**Gboard में असल में:**
- menu item **"Switch to vertical toolbar"** / **"Switch to horizontal toolbar"** ← दोनों तरफ़ के explicit items
- action ID **`widget_change_widget_orientation`**
- drag support: **"Hold and drag to move toolbar"** (Hindi: *"टूलबार को एक जगह से दूसरी जगह ले जाने के लिए,
  उसे दबाकर खींचें और सही जगह पर छोड़ें"*)
- position persist: **`widget_x_position`, `widget_y_position`** + `REGEX|.*widget_x_position_.+`
  (यानी per-keyboard/per-locale scoped prefs)
- orientation-specific feature flags — यह सबसे interesting हैं:
  ```
  VOICE_enable_vertical_widget
  VOICE_enable_vertical_widget_landscape
  VOICE_enable_vertical_widget_foldable
  VOICE_enable_vertical_widget_foldable_landscape
  (इसी तरह PK_* = physical keyboard, STYLUS_* = stylus के लिए)
  ```
  → **vertical widget landscape और foldable पर अलग-अलग gate है**, यानी Google orientation/device-state के
  हिसाब से vertical mode को control करता है।
- debug/dev flags:
  ```
  "Force the horizontal toolbar"
  "Force the toolbar in horizontal mode and disable dragging"
  "Vertical offset of docked horizontal toolbar"
  "An integer value in pixels used to move the docked horizontal toolbar down."
  enable_always_show_dragging_toolbar_tooltip
  enable_always_show_pk_toolbar_orientation_tooltip
  ```
  → साबित होता है कि pill **docked** होती है (bottom / left / right edge), और उसका **offset** तक configurable है।
- 9to5Google: *"You're able to move this bar to the side of your screen as a vertical pill"*
  और (Mar 2024 tablet वाला) *"shrinks the keyboard into a floating pill that you can dock at the bottom or
  left/right of your screen"*

> ⚠️ **एक फ़र्क़ जो तुम्हें पता होना चाहिए:** Gboard में edge तक drag करने पर **auto-flip** होता है या नहीं,
> यह strings से 100% साबित नहीं होता — वहाँ explicit menu item ("Switch to vertical toolbar") और
> `widget_change_widget_orientation` action पक्का है, drag से position बदलना पक्का है।
> तुम्हारा "drag करो → auto-flip, center की ओर खींचो → auto-horizontal" एक **behatar UX addition** है
> (Gboard से आगे)। तकनीकी रूप से पूरी तरह संभव — §7 में इसका exact algorithm है।

---

## भाग 5 — Drag, dock और orientation-flip का असली mechanism

### 5.1 Data model
```
WidgetPosition { x: Float, y: Float, orientation: HORIZONTAL|VERTICAL, docked: BOTTOM|LEFT|RIGHT|FLOATING }
persist → SharedPreferences  (Gboard: widget_x_position / widget_y_position, per-keyboard scoped)
```

### 5.2 Drag (pixel-perfect, बिना lag)
```kotlin
// onTouchEvent — View.setTranslationX/Y पर raw delta, LayoutParams बदलने की देर नहीं
ACTION_DOWN:   downRawX = ev.rawX; downRawY = ev.rawY
               startX = pill.translationX; startY = pill.translationY
               longPressDetector.postDelayed(400)        // "Hold and drag" tooltip के लिए
ACTION_MOVE:   pill.translationX = startX + (ev.rawX - downRawX)
               pill.translationY = startY + (ev.rawY - downRawY)
               checkEdgeThreshold()                      // live flip decision
ACTION_UP:     snapToNearestEdge()                       // animate करके dock
               persistPosition()
```
**ज़रूरी बातें:**
- `ev.rawX/rawY` use करो (view-local `getX()` नहीं) — वरना drag के दौरान view खुद move होने से delta गड़बड़ होता है
- drag के दौरान **`requestLayout()` मत करो** — सिर्फ़ `translationX/Y` (hardware-accelerated, 0 jank)
- `ViewConfiguration.getScaledTouchSlop()` से पहले drag शुरू मत करो, वरना tap भी drag बन जाएगा
- IME window की frame पूरी स्क्रीन है, इसलिए pill **कहीं भी** जा सकती है — कोई clipping नहीं
- window के अंदर coordinates ↔ screen coordinates में मत उलझना: `getLocationInWindow()` से
  `touchableRegion` के लिए screen-space Rect निकालो

### 5.3 Edge threshold + auto-flip (तुम्हारा spec: 24–32dp)
```kotlin
val EDGE_DP = 28f                                  // 24–32dp range का मध्य
val edgePx = EDGE_DP * density
val flipHysteresis = 8.dp                          // ★ flip-flop रोकने के लिए ज़रूरी

fun checkEdgeThreshold() {
    val l = pillLeftOnScreen; val r = screenW - pillRightOnScreen
    val nearEdge = min(l, r) < edgePx

    if (nearEdge && orientation == HORIZONTAL && dragDistance > flipHysteresis) {
        animateToOrientation(VERTICAL)             // 150–250ms
    } else if (!nearEdge && orientation == VERTICAL
               && distFromEdge > edgePx + flipHysteresis) {
        animateToOrientation(HORIZONTAL)           // drag-away → वापस horizontal
    }
}
```
**Hysteresis क्यों ज़रूरी है:** threshold एक ही रखोगे तो pill ठीक boundary पर बार-बार
horizontal↔vertical फड़फड़ाएगी (flicker)। अलग-अलग enter/exit threshold रखो (जैसे enter = 28dp, exit = 40dp)।

### 5.4 Dock / snap
- `ACTION_UP` पर सबसे नज़दीकी edge (bottom / left / right) पर spring-animate करो
- safe-area का ध्यान रखो: **पिछला `padding-project` यहीं काम आएगा** — gesture-nav pill area के अंदर
  dock न होने दो (`systemGestures` / `navigationBars` insets से margin निकालो)
- rotation/fold पर position re-clamp करो (`onConfigurationChanged` → screen bounds में वापस लाओ)

### 5.5 Orientation flip animation
दो तरीके:
| तरीका | कैसा |
|---|---|
| **A. Cross-fade + size animate** (आसान, robust) | दोनों layouts पहले से inflate; `ChangeBounds` + `Fade` transition से 200ms में swap |
| **B. Container transform** (Gboard जैसा fluid) | Material `ContainerTransform` / अपने `ValueAnimator` से width↔height + child positions interpolate; children को `translationX/Y` + `alpha` से उड़ते हुए दिखाओ |

Gboard में `.non-linear-scale` class कई element names में है → **non-linear (Material emphasized) easing** use होती है:
`FastOutSlowInInterpolator` की जगह `PathInterpolator(0.05f, 0.7f, 0.1f, 1f)` (Material emphasized decelerate)।
Duration: **150–250ms** (तुम्हारा spec सही है; Material motion के standard durations भी यही हैं)।

---

## भाग 6 — Recognition engine: Gboard असल में क्या use करता है

| Evidence (APK से) | मतलब |
|---|---|
| `res/NGA.xml`, `res/NgA.xml` | **NGA = Next Generation Assistant** — Google का on-device speech stack |
| `dictation_feature_split.apk` → `lib/arm64-v8a/libdictation_jni.so` (4.08 MB) | **on-device dictation engine** एक अलग Play feature-split में है (dynamic delivery) |
| `dictation_type_traditional`, `dictation_type_jetson` | दो recognition backends (Jetson = नया on-device stack) |
| `enable_ondevice_voice` | on-device voice का feature flag |
| `enable_enhanced_voice_typing` + `_auto_punctuation`, `_automatic_language_switching`, `_speech_enhancement` | punctuation, **auto language switch**, noise-enhancement |
| `enhanced_voice_typing_prefer_detect_language` | भाषा खुद detect करने की प्राथमिकता |
| `seamless_voice_typing`, `enable_global_direct_to_dictation` | keyboard खुलते ही सीधे dictation शुरू |
| `dictation_debug_audio_dump`, `dictation_share_debug_audio` | internal audio debugging |
| `"Faster voice typing, Offline dictation, Local speech"` | offline/local speech का user-facing description |
| `"Assistant voice typing, NGA, Smart dictation"` | search keywords — तीनों नाम एक साथ |

**MgBoard के लिए मतलब:** तुम्हें NGA चाहिए नहीं (वो Google-internal है)। तुम public
`android.speech.SpeechRecognizer` + `RecognizerIntent` use करोगे — वही काम करेगा, बस on-device/offline
capability device के Google app पर निर्भर करेगी (`EXTRA_PREFER_OFFLINE` set कर सकते हो)।

### 6.1 तुम्हारा recognition flow (मौजूदा MgBoard, unchanged)
```
mic ON  → SpeechRecognizer, locale = hi-IN  (Hindi/Gondi mode)
        → onPartialResults / onResults
        → convertDevanagariToGondi(transcript)   ← ★ मौजूदा converter, छेड़ना नहीं
        → nukta rules (मौजूदा)
        → InputConnection.commitText(gondText)
        → बाकी modes: locale = en-IN
```
**Voice pill से इसमें कोई बदलाव नहीं** — pill सिर्फ़ वही transcript उसी pipeline में डालेगी।
यही तुम्हारा "§4 Recognition Logic (मौजूदा से बिल्कुल अपरिवर्तित)" requirement है।

### 6.2 ⚠️ InputConnection की असली दिक्कत (इसे हल्के में मत लेना)
pill mode में IME window collapsed है, पर **InputConnection तब भी ज़िंदा है** जब तक editor focused है।
पर दो दिक्कतें:
1. `onFinishInput()` / `onStartInput()` बार-बार होंगे (app switch पर) → pill की state **prefs में** रखो,
   view hierarchy में नहीं, वरना हर बार खो जाएगी
2. कुछ apps (Chrome के某些 fields, WebView) `InputConnection` जल्दी तोड़ देते हैं →
   `commitText` से पहले `getCurrentInputConnection() == null` check करो, और null हो तो
   transcript को **pending buffer** में रखो; अगला `onStartInput` आते ही flush करो
   (Gboard यही करता है — इसीलिए pill "persistent" लगती है)

---

## भाग 7 — Backspace, mic toggle, keyboard icon (exact function behavior)

| Control | Gboard का behavior | APK evidence | MgBoard spec |
|---|---|---|---|
| **⌫ Backspace** | tap = 1 char delete; **hold = repeat** | `widget_delete`; `"Backspace ontdoen outoregstel"` (undo-autocorrect variant भी है) | ✅ तुम्हारा: पहला repeat **400ms**, फिर हर **70ms** — **मौजूदा MgBoard timing reuse करो**, नया मत बनाओ |
| **🎤 Mic** | tap = start/stop | `"Stop dictation"`, `"Exit dictation mode"`, `"Just speak naturally"` | ✅ तुम्हारा: tap = **pause/resume toggle**, pause पर pill बंद न हो, सिर्फ़ status बदले |
| **⌨ Keyboard icon** | tap → full keyboard वापस | `widget_hide_keyboard`; *"Quickly switch from the toolbar to the keyboard. Just tap the keyboard icon."* | ✅ तुम्हारा: tap → pill **पूरी तरह dismiss**, normal typing mode |
| **भाषा-badge (बाएँ, बड़ा)** | tap → popup menu | `widget_more_access_points`, `widget-popup-menu-*`, *"Open more voice options"* | ✅ तुम्हारा: menu खुले |
| **छोटा भाषा-tag (mic के कोने पर)** | display-only | `.label.secondary.text-size-very-tiny.for-widget` | ✅ तुम्हारा: सिर्फ़ display — active recognition locale बताए |
| **˅˅ chevron (panel में)** | collapse → pill | `"Collapse keyboard"`, `"Minimize toolbar and hide suggestions"` | ✅ तुम्हारा: **एकमात्र trigger** pill दिखने का |
| **Status text** | Speak now / Paused / Listening… / suggestions | exact strings मिलीं | ✅ तुम्हारा: "रोकी गई"/"Paused" |
| **⟲ Orientation** | menu से + drag से | `widget_change_widget_orientation`, `Switch to vertical/horizontal toolbar` | ✅ तुम्हारा: drag-only (कोई बटन नहीं) |
| **Tooltip** | पहले drag पर सिखाता है | `"Hold and drag to move toolbar"`, `enable_always_show_dragging_toolbar_tooltip`, `.widget-tooltip-button.{borderless,neutral-with-border,positive-with-border}` | 💡 **suggest:** MgBoard में भी पहली बार दिखाओ — user को drag पता ही नहीं चलेगा वरना |

**Backspace-hold का सही implementation** (मौजूदा MgBoard handler reuse):
```kotlin
// pill के backspace पर वही listener लगाओ जो मौजूदा KeyboardView के ⌫ पर है
// नया Handler/Runnable मत बनाओ — वरना 400/70ms timing दो जगह maintain करनी पड़ेगी
pillBackspace.setOnTouchListener(existingBackspaceRepeatListener)
```

---

## भाग 8 — Exact strings (copy-paste ready)

### 8.1 English (APK से verbatim)
```
Speak now                        Paused                        Listening…
Just speak naturally             Stop dictation                Exit dictation mode
Collapse keyboard                Minimize keyboard             Minimize toolbar and hide suggestions
Show toolbar                     Toolbar                       Show clipboard
Show translate                   Show emoji                    Show voice commands
All voice commands               Hide voice commands           Close voice command list
Switch to vertical toolbar       Switch to horizontal toolbar  Hold and drag to move toolbar
Open more voice options          More voice options opened     More voice options closed
Close more voice options         Hide your keyboard while voice typing
Quickly switch from the toolbar to the keyboard. Just tap the keyboard icon.
To quickly find the toolbar, tap MINIMIZE minimize icon on your keyboard while voice typing
Use the toolbar for easy voice typing
Enable voice toolbar and start voice typing automatically when keyboard is shown
Force the toolbar in horizontal mode and disable dragging
Vertical offset of docked horizontal toolbar
An integer value in pixels used to move the docked horizontal toolbar down.
Symbols  SYMBOLS  Symbol keyboard  Symbols mode
Recent   Numbers  Brackets         Arrows   Mathematics   List
Unsupported voice command          Voice command to rephrase the text
Go to a text field to use dictation
Faster voice typing, Offline dictation, Local speech
Assistant voice typing, NGA, Smart dictation
```

### 8.2 ⭐ Hindi (APK से verbatim) — MgBoard के strings.xml में सीधे डालो
```
अब बोलें                                            ← "Speak now"  ★
रोकी गई                                              ← "Paused"      ★
बोली को लिखाई में बदलने की सुविधा रोकी गई            ← "Voice typing paused"
टूलबार                                              ← "Toolbar"
टूलबार दिखाएं                                        ← "Show toolbar"
टूलबार को छोटा करें और सुझावों को छिपाएं             ← "Minimize toolbar and hide suggestions"
वर्टिकल टूलबार पर स्विच करें                         ← "Switch to vertical toolbar" ★
क्षैतिज                                             ← "Horizontal"
क्लिपबोर्ड दिखाएं                                    ← "Show clipboard"   ★
अनुवाद दिखाएं                                        ← "Show translate"   ★
इमोजी दिखाएं                                         ← "Show emoji"       ★
सेटिंग                                              ← "Settings"         ★
चिह्न                                               ← "Symbols"          ★
टूलबार को एक जगह से दूसरी जगह ले जाने के लिए, उसे दबाकर खींचें और सही जगह पर छोड़ें   ← drag tooltip ★
टूलबार से कीबोर्ड पर तेज़ी से स्विच करें. इसके लिए, बस कीबोर्ड आइकॉन पर टैप करें.      ← keyboard icon ★
टूलबार खोजने के लिए, बोली को लिखाई में बदलने की सुविधा इस्तेमाल करने के दौरान,
   अपने कीबोर्ड पर छोटा करें पर टैप करें               ← onboarding
बोलेर दिइने… (voice commands के कई variants)
```
★ = तुम्हारे spec में माँगे गए items

### 8.3 ⚠️ एक जगह तुम्हारा spec Gboard से अलग है
तुम्हारे spec में State 1 का टेक्स्ट **"बोल रहा हूं / Speak now"** है।
Gboard APK में **"बोल रहा हूं" कहीं नहीं है** (0 matches) — असली string है **"अब बोलें"**।
फ़ैसला तुम्हारा: Gboard-exact चाहिए तो "अब बोलें", अपनी पसंद चाहिए तो "बोल रहा हूं" रखो
(दोनों में से एक `strings.xml` में, ताकि बाद में बदलना आसान रहे)।

### 8.4 भाषा-सूची (menu के divider के बाद)
तुम्हारे spec में: `English (India) ✓, हिन्दी (भारत), हिन्दी, हिन्दी (भारत) संक्षिप्त`
APK में यही pattern मौजूद है — Gboard हर language के लिए `<भाषा> (<देश>)` और कुछ के लिए
"संक्षिप्त/compact" variants रखता है (यह Gboard की enabled-languages list से आता है, कोई hardcoded list नहीं)।
→ MgBoard में यह list **तुम्हारी मौजूदा enabled-languages prefs** से generate करो।

---

## भाग 9 — ⚠️ §5 "System-wide Floating Overlay" की reality check (सबसे ज़रूरी हिस्सा)

तुम्हारे prompt में लिखा है:
> *"Pill (horizontal और vertical दोनों states में) किसी भी दूसरे app (Chrome, WhatsApp, आदि) के ऊपर तैरनी चाहिए,
> सिर्फ़ keyboard के अंदर सीमित न रहे। इसके लिए WindowManager + सही overlay-type
> (IME extraction window / जहाँ ज़रूरी हो TYPE_APPLICATION_OVERLAY) इस्तेमाल करें।"*

**Research का साफ़ नतीजा: Gboard ऐसा नहीं करता — और उसके पास overlay permission है ही नहीं।**

### 9.1 तीन विकल्प, सच्चे trade-offs के साथ

| | **(A) IME-window widget** ← *Gboard यही करता है* | **(B) `TYPE_APPLICATION_OVERLAY`** | **(C) Hybrid** |
|---|---|---|---|
| Permission | **कुछ नहीं** ✅ | `SYSTEM_ALERT_WINDOW` ("Display over other apps") — user को Settings में manually देना पड़ता है | दोनों |
| Chrome/WhatsApp के ऊपर | ✅ हाँ (जब तक IME bound है) | ✅ हाँ, हमेशा | ✅ |
| कोई text field focused न हो तब | ❌ pill चली जाती है (IME hide) | ✅ बनी रहती है | ✅ |
| Back button / launcher पर | ❌ चली जाती है | ✅ बनी रहती है | ✅ |
| Text insert करना | ✅ सीधे `InputConnection` | ⚠️ मुश्किल — overlay से focused editor तक `InputConnection` नहीं मिलता; `AccessibilityService` या commit-trick चाहिए | ✅ |
| Mic चालू रखना (Android 14+) | ✅ IME visible = "while-in-use", कोई FGS नहीं चाहिए | ❌ background माना जाएगा → `foregroundServiceType="microphone"` + `FOREGROUND_SERVICE_MICROPHONE` चाहिए, और **background से FGS start ही नहीं हो सकता** (Android 12+) | ✅ |
| Play Store policy | ✅ साफ़ | ⚠️ overlay permission की घोषणी + scrutiny; keyboards के लिए अक्सर reject | ⚠️ |
| Z-order | app windows के ऊपर, system bars के नीचे | सबसे ऊपर (system dialogs को भी ढक सकता है) | — |
| Complexity | कम | ज़्यादा | सबसे ज़्यादा |

### 9.2 "IME extraction window" वाले सुझाव पर सच्चाई
तुम्हारे prompt में "IME extraction window" का ज़िक्र है। असलियत:
- `InputMethodService` का extraction view (`onCreateExtractTextView`, `isExtractViewShown()`)
  **सिर्फ़ fullscreen/landscape extract mode** के लिए है — यह एक अलग free-floating window **नहीं** है,
  वही IME window का हिस्सा है।
- इसलिए उससे "हर app के ऊपर तैरती pill" नहीं बन सकती।

### 9.3 असली sub-window विकल्प (कम लोग जानते हैं)
IME window से **attached sub-window** बना सकते हो, बिना overlay permission के:
```java
WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
lp.token = inputView.getWindowToken();                              // ★ IME का token
lp.type  = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG;  // या TYPE_APPLICATION_PANEL
wm.addView(pillView, lp);
```
- फ़ायदा: pill **IME window से अलग** होकर उसकी bounds के बाहर भी position ले सकती है, और app windows के ऊपर रहती है
- नुकसान: **IME window मरते ही यह भी मर जाती है** (token खत्म) → यानी (A) वाली सीमा वैसी ही
- ट्रैप: इससे `AlertDialog` बनाने की कोशिश करने पर कुछ apps (Chrome) में IME बंद हो जाता है —
  `FLAG_ALT_FOCUSABLE_IM` लगाना पड़ता है

### 9.4 मेरी सलाह (MgBoard के लिए)
**Phase 1 में (A) बनाओ — Gboard-exact, शून्य permission, शून्य Play-policy risk।**
"Persistent" का मतलब Gboard में भी यही है: mode prefs में याद रहता है, अगला text field खुलते ही pill वापस।

अगर आगे चलकर सच में "हर जगह तैरती pill" चाहिए (जैसे **Flow** app जो 2026 में आया — वो floating button
किसी भी app के ऊपर रखता है), तब (B) को **optional, user-opt-in** feature बनाओ:
```
Settings → "Voice pill को किसी भी app पर तैराएँ" → canDrawOverlays() check → ACTION_MANAGE_OVERLAY_PERMISSION
```
और तब Android 14+ के mic rules भी संभालो (§9.5)।

### 9.5 Battery / Doze / mic — असली नियम (तुम्हारे prompt का आख़िरी हिस्सा)

| स्थिति | नियम |
|---|---|
| IME visible है (pill दिख रही है) | app **"while-in-use"** है → mic बिना किसी foreground service के चलेगा ✅ |
| IME hidden, overlay pill दिख रही है | app background है → `RECORD_AUDIO` while-in-use permission **नहीं** चलेगी; चाहिए `foregroundServiceType="microphone"` + `FOREGROUND_SERVICE_MICROPHONE`, और **Android 12+ पर background से FGS start करना ही मना है** (कुछ exemptions के साथ) → `ForegroundServiceStartNotAllowedException` |
| Doze | `WAKE_LOCK` लेने से UI नहीं बचता; Doze में network+CPU कटता है → cloud recognition रुक जाएगी। **on-device recognition (`EXTRA_PREFER_OFFLINE`) यहीं काम आता है** |
| Android 12+ privacy | mic use होते ही status bar में **green dot** दिखेगी — यह unavoidable है (और होना चाहिए) |
| Android 15/16 edge-to-edge | pill को bottom पर dock करते वक़्त **`padding-project` वाले gesture insets** use करो, वरना pill system gesture area में दब जाएगी |
| Play Console | targetSdk 34+ हो तो FGS types की घोषणी Play Console में करनी पड़ती है |

**व्यावहारिक सलाह:** pill जब तक IME window में है (option A), यह सारा झंझट **शून्य** है।
Battery का असली बचाव = recognition बंद होते ही `SpeechRecognizer.destroy()`, और
`onWindowHidden()`/`onFinishInputView()` में mic release करो (Gboard में `WAKE_LOCK` इसीलिए है)।

---

## भाग 10 — MgBoard के लिए recommended architecture

### 10.1 Package layout
```
com.mgboard.ime.voice/
├── VoiceWidgetState.kt          // enum + sealed state (नीचे)
├── VoiceWidgetController.kt     // ★ brain: state machine, prefs, transitions
├── VoiceWidgetHostView.kt       // FrameLayout: horizontal/vertical दोनों layouts रखता है
├── HorizontalPillView.kt        // भाषा-badge | ⌨ | status | ⌫ | 🎤
├── VerticalPillView.kt          // 🎤 | ⌫ | ⌨ | भाषा-badge
├── VoicePanelView.kt            // State 1: full panel (chevron, badge, mic circle)
├── VoicePopupMenu.kt            // State 3: PopupWindow, upward gravity
├── SymbolsOverlayView.kt        // State 4: header + X + grid + category tabs
├── WidgetDragController.kt      // §5.2/5.3 का drag + edge-flip + snap
├── WidgetPositionStore.kt       // x, y, orientation persist (Gboard: widget_x_position…)
└── DictationBridge.kt           // SpeechRecognizer ↔ मौजूदा convertDevanagariToGondi() pipeline
```

### 10.2 State machine
```kotlin
sealed interface VoiceWidgetState {
    data object Hidden                : VoiceWidgetState
    data object FullVoicePanel        : VoiceWidgetState   // State 1
    data class  HorizontalPill(
        val mic: MicState,            // LISTENING | PAUSED | ERROR | OFFLINE
        val status: String,           // "अब बोलें" / "रोकी गई" / partial transcript
        val dockedTo: Dock            // BOTTOM | FLOATING
    ) : VoiceWidgetState                                                     // State 2
    data class  MenuOpen(val over: VoiceWidgetState) : VoiceWidgetState      // State 3
    data class  SymbolsOverlay(val category: SymbolCategory) : VoiceWidgetState // State 4
    data class  VerticalPill(
        val mic: MicState, val dockedTo: Dock          // LEFT | RIGHT
    ) : VoiceWidgetState                                                     // State 5
}
enum class MicState { LISTENING, PAUSED, ERROR, OFFLINE }
```

### 10.3 Transition table (सब animated, 150–250ms)

| From | Event | To | Animation |
|---|---|---|---|
| Hidden | mic access-point tap | FullVoicePanel | keyboard के साथ normal IME animation |
| FullVoicePanel | ˅˅ chevron tap | HorizontalPill(bottom) | **collapse**: height → pill height, children fade+slide (Material emphasized) |
| HorizontalPill | ⌨ tap | Hidden (+ full keyboard) | pill fade-out, keyboard expand |
| HorizontalPill | 🎤 tap | HorizontalPill(PAUSED) | सिर्फ़ status text cross-fade + mic icon color |
| HorizontalPill | भाषा-badge tap | MenuOpen | PopupWindow upward, scale+fade 180ms; **mic auto-pause** |
| MenuOpen | "Switch to vertical" | VerticalPill | container transform (rotate नहीं — layout morph) |
| MenuOpen | "Symbols" | SymbolsOverlay | overlay slide-up; pill नीचे अपनी last status के साथ visible रहे |
| MenuOpen | बाहर tap / back | पिछला state | fade 150ms |
| HorizontalPill | drag → edge ≤28dp | VerticalPill | flip + snap (§5.3) |
| VerticalPill | drag → edge से दूर >40dp | HorizontalPill | flip back |
| कोई भी | rotation / fold | वही state, re-clamped | position animate |
| कोई भी | `onFinishInput` | prefs में save | — |
| Hidden | `onStartInputView` + `pillModeEnabled` | पिछला state restore | — |

### 10.4 IME service में wiring (सिर्फ़ जोड़ना है, मौजूदा कुछ नहीं बदलना)
```kotlin
class MgboardImeService : InputMethodService() {

    private val voiceWidget by lazy { VoiceWidgetController(this) }   // ★ नया module

    override fun onCreateInputView(): View {
        val root = layoutInflater.inflate(R.layout.mgboard_keyboard_root, null)
        // …मौजूदा keyboard setup, बिल्कुल जैसा है वैसा…
        voiceWidget.install(root)          // host view + drag + insets sync
        return root
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)   // मौजूदा behavior untouched
        voiceWidget.restoreIfEnabled()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        voiceWidget.saveState()
        super.onFinishInputView(finishingInput)
    }

    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        voiceWidget.applyTouchRegion(outInsets)    // ★ pill-mode में pass-through (§2.4)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        voiceWidget.onConfigurationChanged()       // re-clamp + insets
    }

    override fun onDestroy() { voiceWidget.release(); super.onDestroy() }
}
```
**मौजूदा चीज़ें जो बिल्कुल नहीं छूनीं:** `KeyboardView`, key-event pipeline, `KeyboardSwitcher`,
`convertDevanagariToGondi()`, nukta rules, backspace-repeat handler (इसे **reuse** करना है, copy नहीं),
fonts (`Noto Sans Masaram Gondi`), toolbar/access-points।

### 10.5 मौजूदा helpers reuse करो (दोबारा मत बनाना)
| चाहिए | कहाँ से लो |
|---|---|
| Backspace 400ms/70ms repeat | मौजूदा `KeyboardView` का repeat handler → pill के ⌫ पर वही listener |
| Text insert + Gondi conversion | मौजूदा `commitText` path / `convertDevanagariToGondi()` |
| Theme colors | मौजूदा theme manager (pill का background वही हो, वरना mismatch दिखेगा) |
| Safe-area insets | **`padding-project` का `GestureNavPaddingController`** — pill को gesture area में dock होने से बचाओ |
| Language list (menu) | मौजूदा enabled-languages prefs |

---

## भाग 11 — Implementation plan (phases)

| Phase | क्या | Effort |
|---|---|---|
| **0** | Decisions confirm करो (§12) + branch बनाओ; baseline में मौजूदा typing behavior के 5 manual tests note करो | 0.5d |
| **1** | `DictationBridge` — `SpeechRecognizer` (hi-IN / en-IN) → मौजूदा Gondi pipeline। बिना UI, सिर्फ़ logs से verify | 1–2d |
| **2** | State 1 Full Voice Panel (chevron, badge, mic circle, status text) | 1–2d |
| **3** | State 2 Horizontal Pill + collapse animation + `onComputeInsets` touch-region | 2–3d |
| **4** | `WidgetDragController` — drag, edge-threshold flip, snap, persist position | 2d |
| **5** | State 5 Vertical Pill + orientation morph animation | 1–2d |
| **6** | State 3 Popup menu (+ mic auto-pause) + language list + checkmark | 1–2d |
| **7** | State 4 Symbols overlay (header, X, grid, category tabs) | 1–2d |
| **8** | Polish: tooltip/onboarding, dark theme, foldable/landscape, gesture-inset dock safety, TalkBack labels | 2d |
| **9** | §13 test matrix + regression (मौजूदा typing/conversion untouched है या नहीं) | 1d |

कुल: ~12–17 dev-days (एक developer)।

---

## भाग 12 — Decisions जो implement से पहले confirm करने हैं

1. **Window strategy:** (A) IME-window widget = Gboard-exact, 0 permission ← *मेरी सलाह* /
   (B) overlay permission के साथ सच में हर जगह / (C) hybrid (A पहले, B बाद में opt-in)
2. **State 1 का status text:** `"अब बोलें"` (Gboard-exact) या `"बोल रहा हूं"` (तुम्हारा spec)
3. **Menu में 2 extra items चाहिए?** Gboard में हैं: `Show voice commands` + `Feedback` (तुम्हारे spec में नहीं)
4. **Edge-flip threshold:** 28dp (24–32 के बीच) + hysteresis 8dp — ठीक है?
5. **Language:** Kotlin / Java; UI: custom View / Compose
6. **Symbols overlay के पीछे pill दिखे** (तुम्हारा spec कहता है हाँ) — तो overlay को **full-screen dim + pill visible**
   रखना होगा; confirm करो
7. **Onboarding tooltip** ("Hold and drag…") चाहिए या नहीं (Gboard में है, flag-gated)
8. **Offline/on-device recognition** की कोशिश करें (`EXTRA_PREFER_OFFLINE`)? device-dependent होगा

---

## भाग 13 — Test matrix

**States**
- [ ] mic tap → State 1 खुले, status "अब बोलें", नीचे keyboard दिखे
- [ ] ˅˅ tap → 200ms में State 2, कोई hard cut नहीं
- [ ] 🎤 tap → status "रोकी गई", **pill गायब न हो**
- [ ] 🎤 दोबारा → listening resume, वही session
- [ ] ⌫ tap → 1 char delete; ⌫ hold → 400ms बाद पहला repeat, फिर हर 70ms (**मौजूदा timing से exact match**)
- [ ] ⌨ tap → pill पूरी तरह dismiss, normal typing mode, background में कुछ न बचे
- [ ] भाषा-badge tap → menu **ऊपर** की ओर खुले, **mic auto-pause हो**
- [ ] menu → symbols → header "चिह्न" + X + grid + tabs; **पीछे pill last status के साथ visible**
- [ ] symbols के category tabs smooth horizontal switch
- [ ] menu → "वर्टिकल टूलबार पर स्विच करें" → State 5, order 🎤/⌫/⌨/badge

**Drag & flip**
- [ ] drag pixel-perfect, कोई lag/jitter नहीं (60fps; `adb shell dumpsys gfxinfo <pkg>` से check)
- [ ] edge से ≤28dp → auto-vertical; center की ओर >40dp → auto-horizontal
- [ ] threshold पर बार-बार आने-जाने से **flicker न हो** (hysteresis काम कर रहा है)
- [ ] छोड़ने पर nearest edge पर snap; position app-restart के बाद भी याद रहे
- [ ] drag करते वक़्त pill के बाकी स्क्रीन पर tap **app को जाए** (pass-through), pill पर tap pill को

**System / robustness**
- [ ] Chrome / WhatsApp / Telegram / किसी WebView में pill काम करे; text सही field में insert हो
- [ ] app switch (WhatsApp → Chrome) पर pill state बची रहे; `InputConnection` null हो तो transcript pending buffer में
- [ ] back दबाने पर pill जाए; अगला text field खुलते ही **पिछला mode restore** हो
- [ ] rotation (portrait ↔ landscape) + foldable fold/unfold पर position re-clamp, pill स्क्रीन के बाहर न जाए
- [ ] dark/light theme दोनों में pill का background, ripple, pill-contrast सही
- [ ] Android 8 (minSdk 26) से Android 16 तक; gesture nav + 3-button nav दोनों पर dock safe
  (→ `padding-project` के insets use हुए हैं या नहीं)
- [ ] TalkBack: हर बटन पर content-description ("अन्य वॉइस विकल्प खोलें" वगैरह)
- [ ] mic permission denied → graceful error state, crash नहीं
- [ ] offline (airplane mode) → on-device recognition या साफ़ "offline" state
- [ ] Android 12+ green mic indicator दिखे (expected), और pill बंद करते ही हट जाए

**★ Regression (सबसे ज़रूरी — तुम्हारा अपना नियम)**
- [ ] Devanagari→Gondi conversion पहले जैसा ही (वही test sentences)
- [ ] nukta rules unchanged
- [ ] hold-backspace 400ms/70ms unchanged (normal keyboard पर भी)
- [ ] मौजूदा keyboard layout, keys, फ़ॉन्ट (Noto Sans Masaram Gondi), toolbar elements — pixel-identical
- [ ] typing speed/latency पर कोई असर नहीं (`voice` module lazy-init है, `onCreateInputView` में भार नहीं बढ़ा)

---

## भाग 14 — Sources

**Primary (मैंने खुद खोला):**
- `Gboard 18.3.1.977415014-beta-arm64-v8a` APK (versionCode 176004238) — APKCombo/Cloudflare R2 से download;
  `AndroidManifest.xml` (pyaxmlparser AXML) + `resources.arsc` (custom string-pool parser, 220,375 strings) +
  split APK list (`dictation_feature_split.apk` → `libdictation_jni.so`)
- AOSP `frameworks/base/core/java/android/inputmethodservice/InputMethodService.java` (android14-release) —
  `Insets` class (contentTopInsets / visibleTopInsets / touchableInsets / touchableRegion), window LayoutParams,
  `onComputeInsets`, `setImeExclusionRect`
- AOSP `frameworks/base/services/core/java/com/android/server/wm/InsetsPolicy.java`, `WindowLayout.java`

**Secondary (teardowns / docs):**
- 9to5Google — *Gboard's Assistant voice typing toolbar is coming to Pixel phones* (Feb 10, 2025): पूरा menu list, persistence
- 9to5Google — *Your Android phone has a new way to type with your voice* (Apr 15, 2025): hamburger menu, vertical toolbar
- 9to5Google — *Gboard preps 'Seamless voice typing'* (Jan 22, 2024): floating pill persists across apps
- 9to5Google — *'Advanced voice typing'* (Apr 9, 2025): *"dock at the bottom or left/right of your screen"*
- Android Authority — *Google brings Gboard's voice typing to the forefront with a dedicated toolbar* (Feb 10, 2025):
  beta 15.0.03.717871796, "Speak now"/"Paused", minimize → backspace, 'i' → reopen keyboard
- Android Developers — *Foreground service types are required* (Android 14): microphone FGS rules
- 9to5Google — *Google Pixel ruined voice typing…* (Apr 26, 2026): Flow app = असली overlay वाला alternative
