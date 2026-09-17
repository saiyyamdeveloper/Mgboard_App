# 🔍 SPEC vs GBOARD — line-by-line diff

तुम्हारा **"MgBoard Android — Final Prompt (Gboard-style Voice Pill) ✅ Confirmed"** ↔
**असली Gboard 18.3.1-beta** (APK से verified)

Legend: ✅ हूबहू / 🟡 मेल खाता है पर detail अलग / ⚠️ Gboard में नहीं है (तुम्हारा addition) /
❌ तकनीकी रूप से Gboard जैसा संभव नहीं / 💡 सुझाव

---

## §1 State Machine

| तुम्हारा spec | Gboard असल में | verdict |
|---|---|---|
| **S1:** mic दबाने पर full voice panel खुले | mic दबाने पर keyboard + उसके ऊपर **voice toolbar strip** आता है | ✅ |
| S1: "बोल रहा हूं / Speak now" टेक्स्ट | **"Speak now"** / Hindi **"अब बोलें"** — *"बोल रहा हूं" APK में कहीं नहीं (0/220,375)* | 🟡 **text अलग** |
| S1: नीचे टेक्स्ट-keyboard दिखे | हाँ, keyboard नीचे बना रहता है | ✅ |
| S1: ऊपर-दाएँ ˅˅ double chevron | `Collapse keyboard` / `Minimize toolbar and hide suggestions` — chevron ही है | ✅ |
| S1: भाषा badge | language access point मौजूद है | ✅ |
| S1: नीला mic circle | Android Authority: *"microphone button **featuring Google's colors**"* — नीला नहीं, Google के चारों रंग | 🟡 |
| **S2:** ˅˅ दबाने पर panel → pill, **यही एकमात्र trigger** | Gboard में दूसरे रास्ते भी हैं: `seamless_voice_typing` flag, *"Enable voice toolbar and start voice typing automatically when keyboard is shown"*, और *"tap MINIMIZE icon on your keyboard while voice typing"* | 🟡 MgBoard में single trigger रखना **आसान और साफ़** है — ठीक है |
| S2 order: भाषा-badge — ⌨ — status — ⌫ — 🎤 (mic पर छोटा भाषा-tag) | बाएँ hamburger/language · बीच status · दाएँ ⌫ फिर 🎤 · ⌨ icon भी है (`widget_hide_keyboard`, `widget_keyboard`) | ✅ (exact x-order Gboard में कहीं documented नहीं) |
| S2: sticky — pause पर गायब न हो, status "रोकी गई"/"Paused" | **"Paused"** / Hindi **"रोकी गई"** दोनों APK में verbatim मिलीं; toolbar persistent है | ✅ **perfect match** |
| **S3:** menu ऊपर की ओर खुले | `widget-popup-menu` (PopupWindow) — ऊपर ही खुलता है | ✅ |
| S3 items: सेटिंग्स, क्लिपबोर्ड, अनुवाद, इमोजी, सिंबल-कीबोर्ड, वर्टिकल टूलबार | Settings · Show clipboard · Show translate · Show emoji · Switch to vertical toolbar (+ **Show voice commands**, **Feedback** — तुम्हारे spec में नहीं) · "सिंबल वाला कीबोर्ड" अलग menu item teardown में नहीं मिला (symbols access point अलग है) | 🟡 **2 items कम, 1 item अलग** |
| S3: divider के बाद भाषा-list, selected पर ✓ | popup-menu में language list + `-entry-end-icon` (checkmark) मौजूद है | ✅ |
| S3: menu खुलते ही mic auto-pause | behavior मौजूद है, पर कोई explicit string/flag नहीं मिला → **implicit** है | 🟡 MgBoard में explicitly लिखना पड़ेगा |
| **S4:** "चिह्न" header + X + symbol-grid | `Symbols` / `SYMBOLS` / Hindi **`चिह्न`** (29 बार) / `Close button` | ✅ |
| S4 tabs: history, 1?#, brackets, arrow, math, numbered-list | APK में exact categories: **Recent · Numbers · Brackets · Arrows · Mathematics · List** | ✅ **perfect match** |
| S4: पीछे pill last status के साथ visible रहे | APK में इसका सीधा सबूत नहीं | 💡 तुम्हारा UX decision — implement करना आसान है (overlay को full-screen dim रखो, pill को touchable रखो) |
| **S5:** vertical order 🎤 → ⌫ → ⌨ → भाषा-badge | horizontal का ही उल्टा क्रम, यही logical है | ✅ |
| S5: rounded vertical capsule, screen edge पर float | *"dock at the bottom or **left/right** of your screen"*; `.widget-keyboard.keyboard-background.vertical` | ✅ |
| S5 trigger: **किनारे तक drag → auto-flip; center की ओर drag → auto-horizontal; कोई बटन/double-tap नहीं** | Gboard में पक्का है: **drag** (`Hold and drag to move toolbar`, `widget_x_position/y_position`) और **explicit flip** (`Switch to vertical/horizontal toolbar`, `widget_change_widget_orientation`). *"drag करते ही auto-flip"* का सीधा सबूत APK strings में नहीं मिला | ⚠️ **तुम्हारा UX addition** (Gboard से आगे) — तकनीकी रूप से पूरी तरह संभव, algorithm `RESEARCH.md §5.3` में |

---

## §2 Smooth व Responsive

| तुम्हारा spec | Gboard | verdict |
|---|---|---|
| हर transition animate, कोई hard cut नहीं, 150–250ms ease-in-out | theme elements में `.non-linear-scale` बार-बार → Material **non-linear/emphasized** easing; exact ms APK से पता नहीं चलता | ✅ 150–250ms Material standard के अंदर है |
| drag real-time, pixel-perfect, बिना lag (`onTouchListener` से raw delta) | drag support पक्का है; position prefs में persist | ✅ |
| auto-vertical flip ~24–32dp edge threshold पर | Gboard में dock edges हैं (bottom/left/right); threshold की कोई string नहीं मिली | 🟡 तुम्हारी value ठीक है — **hysteresis ज़रूर जोड़ो** वरना boundary पर flicker होगा |
| pill session भर persistent, जब तक ⌨ से बंद न करे | *"persistent and remains active **the next time you open a text field** until you manually bring back the keyboard"* | 🟡 **परिभाषा ठीक करनी होगी** — देखो नीचे |
| सारी sizing dp-based, hardcoded pixel नहीं | Gboard में एक dev setting खुद कहती है: *"An integer value **in pixels** used to move the docked horizontal toolbar down"* (वो internal debug है) | ✅ dp-based सही फ़ैसला है |

### ⚠️ "session भर persistent" की असली हद
Gboard के पास `SYSTEM_ALERT_WINDOW` नहीं है, इसलिए pill **IME window के अंदर** ही रहती है।
IME window मरता है जब: input-focus चला जाए, user back दबाए, या system IME hide कर दे।
इसलिए असली behavior यह है:

> pill उस वक़्त तक दिखती है जब तक IME bound है। IME hide होते ही pill भी जाती है —
> **पर mode prefs में सेव रहता है**, इसलिए अगला text field खुलते ही pill अपने-आप उसी state में लौट आती है।

यही "persistent" का व्यावहारिक मतलब है, और यही MgBoard में भी मिलेगा (बिना permission माँगे)।

---

## §3 Functional Logic

| तुम्हारा spec | Gboard | verdict |
|---|---|---|
| भाषा-badge tap → menu | `widget_more_access_points` + *"Open more voice options"* | ✅ |
| छोटा भाषा-tag = display only | `.label.secondary.text-size-very-tiny.for-widget` | ✅ |
| ⌫ tap = 1 char; hold = **400ms फिर 70ms** (मौजूदा MgBoard timing) | `widget_delete` मौजूद है; Gboard की exact repeat timing APK से पता नहीं चलती | ✅ तुम्हारा फ़ैसला सही — **मौजूदा handler reuse करो**, नया मत बनाओ |
| 🎤 tap = pause/resume; pause पर pill बंद न हो, सिर्फ़ status बदले | *"Paused"* state मौजूद, pill बनी रहती है | ✅ |
| ⌨ tap → pill **पूरी तरह dismiss**, background में persist नहीं, normal typing mode | *"Quickly switch from the toolbar to the keyboard. Just tap the keyboard icon."* + `widget_hide_keyboard` | ✅ |
| menu खुलते ही mic auto-pause | implicit behavior | 🟡 explicitly implement करना होगा |

---

## §4 Recognition Logic (मौजूदा से अपरिवर्तित)

| तुम्हारा spec | Gboard | verdict |
|---|---|---|
| Hindi/Gondi mode → locale `hi-IN` → `convertDevanagariToGondi()` → Gondi insert | Gboard में Gondi नहीं है (यह MgBoard-specific है) — पर pattern वही है: locale से recognition, फिर post-processing | ✅ approach सही |
| बाकी modes → `en-IN` | vही | ✅ |

### 💡 एक冲突 जिसका ध्यान रखना
Gboard में ये flags हैं:
```
enable_enhanced_voice_typing_automatic_language_switching
enhanced_voice_typing_prefer_detect_language
```
यानी Gboard **भाषा खुद detect** कर लेता है। अगर MgBoard में कभी auto-detect चालू हुआ, तो तुम्हारा
"hi-IN तय है → Gondi conversion" वाला अनुमान टूट सकता है (transcript किसी और भाषा में आ जाएगा और
converter गलत output देगा)।

**सलाह:** MgBoard में auto-language-switch **बंद रखो** (या Gondi mode में explicitly disable करो),
और transcript मिलने के बाद **हमेशा** locale check करो before calling `convertDevanagariToGondi()`.

### 💡 दूसरी बात: Gboard का engine
Gboard **NGA** (Next Generation Assistant) + on-device `libdictation_jni.so` use करता है — वो Google-internal है,
तुम्हें नहीं मिलेगा। MgBoard के लिए public `android.speech.SpeechRecognizer` ही रास्ता है
(चाहो तो `EXTRA_PREFER_OFFLINE` से on-device की कोशिश करो — device के Google app पर निर्भर करेगा)।

---

## §5 System-wide Floating Overlay — ❌ सबसे बड़ा अंतर

| तुम्हारा spec | असलियत |
|---|---|
| *"Pill किसी भी दूसरे app (Chrome, WhatsApp) के ऊपर तैरनी चाहिए, सिर्फ़ keyboard के अंदर सीमित न रहे"* | Gboard **Chrome/WhatsApp के ऊपर तैरती है** ✅ — पर सिर्फ़ तब जब IME bound हो |
| *"इसके लिए WindowManager + सही overlay-type (IME extraction window / जहाँ ज़रूरी हो `TYPE_APPLICATION_OVERLAY`)"* | ❌ **Gboard के पास `SYSTEM_ALERT_WINDOW` permission ही नहीं है** (manifest verified). वह `TYPE_APPLICATION_OVERLAY` use नहीं करता |
| "IME extraction window" | ❌ extraction view (`onCreateExtractTextView`) सिर्फ़ **fullscreen extract mode** के लिए है — वह अलग free-floating window नहीं, वही IME window का हिस्सा है। इससे "हर app पर तैरती pill" नहीं बन सकती |

### Gboard असल में क्या करता है
1. IME window की frame **पूरी स्क्रीन** तक जाती है (`setFitInsetsSides(Side.all() & ~Side.BOTTOM)`, AOSP-verified)
2. उसमें एक छोटा draggable **widget** रखता है, बाकी सब transparent
3. `onComputeInsets()` में `touchableInsets` / `touchableRegion` सेट करके बाकी स्क्रीन पर tap
   **नीचे app को pass-through** कर देता है
4. नतीजा: pill किसी भी app के ऊपर तैरती दिखती है — **बिना किसी permission के**

### तीन विकल्प (पूरा तुलना-तालिका `RESEARCH.md §9.1` में)

| | (A) IME-window widget ← **Gboard-exact** | (B) `TYPE_APPLICATION_OVERLAY` | (C) Hybrid |
|---|---|---|---|
| Permission | **शून्य** | "Display over other apps" माँगना पड़ेगा | दोनों |
| कोई text field focused न हो तब | ❌ pill जाती है | ✅ बनी रहती है | ✅ |
| Text insert | ✅ सीधा `InputConnection` | ⚠️ मुश्किल (overlay से focused editor तक connection नहीं) | ✅ |
| Android 14+ mic | ✅ IME visible = while-in-use | ❌ mic FGS चाहिए, और background से FGS start ही मना है | ✅ |
| Play policy | ✅ साफ़ | ⚠️ घोषणी + scrutiny | ⚠️ |

**मेरी सलाह:** Phase 1 में **(A)** बनाओ। आगे ज़रूरत पड़े तो (B) को **optional user opt-in** बना देना
(`Settings → "pill को किसी भी app पर तैराएँ"` → `canDrawOverlays()` → `ACTION_MANAGE_OVERLAY_PERMISSION`)।

### 💡 एक तीसरा रास्ता जो कम लोग जानते हैं
IME window से **attached sub-window** — बिना overlay permission के, और IME window की bounds से बाहर भी position ले सकता है:
```java
lp.token = inputView.getWindowToken();
lp.type  = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG;   // या TYPE_APPLICATION_PANEL
wm.addView(pillView, lp);
```
पर सीमा वही: IME window मरते ही यह भी मर जाती है। (ट्रैप: `AlertDialog` के साथ कुछ apps में IME बंद हो जाता है —
`FLAG_ALT_FOCUSABLE_IM` चाहिए।)

---

## §5 का आख़िरी हिस्सा — Battery/Doze

| तुम्हारा spec | असलियत |
|---|---|
| *"Battery/Doze optimization का ख़्याल रखें ताकि background में voice-recognition या pill-rendering रुके न"* | Gboard में `WAKE_LOCK` है, पर **कोई mic foreground service नहीं**. वह कभी background में recognize नहीं करता — pill हमेशा IME-visible context में रहती है, इसलिए "while-in-use" नियम से बच जाती है |

**असली नियम:**
- IME visible (pill दिख रही है) → mic बिना FGS के चलेगा ✅
- IME hidden + overlay pill → app background है → Android 14 पर `foregroundServiceType="microphone"` +
  `FOREGROUND_SERVICE_MICROPHONE` चाहिए, और **Android 12+ पर background से FGS start करना ही मना है**
  (`ForegroundServiceStartNotAllowedException`)
- Doze में network/CPU कटता है → **cloud recognition रुक जाएगी**; on-device (`EXTRA_PREFER_OFFLINE`) ही बचाव है
- Android 12+ पर mic use होते ही status bar में **green dot** दिखेगी — unavoidable (और सही भी)
- असली battery बचाव = recognition बंद होते ही `SpeechRecognizer.destroy()`, और
  `onWindowHidden()` / `onFinishInputView()` में mic release

---

## ⚠️ सबसे ज़रूरी नियम (तुम्हारा खुद का) — verdict: ✅ पूरी तरह निभ सकता है

> *"MgBoard के मौजूदा सभी typing/conversion behaviors — Devanagari→Gondi converter, nukta नियम,
> hold-backspace timing (400ms/70ms), मौजूदा keyboard layout, keys, फ़ॉन्ट (Noto Sans Masaram Gondi),
> toolbar elements — पूरी तरह unchanged रहेंगे।"*

यह **100% संभव** है, क्योंकि यह feature purely **additive** है:

| मौजूदा चीज़ | क्या करना है |
|---|---|
| `KeyboardView`, key-event pipeline, `KeyboardSwitcher` | **छूना ही नहीं** |
| `convertDevanagariToGondi()`, nukta rules | **छूना ही नहीं** — pill सिर्फ़ वही transcript इसी pipeline में डालेगी |
| hold-backspace 400ms/70ms | **reuse करो** — pill के ⌫ पर वही मौजूदा listener लगाओ, नया Handler मत बनाओ |
| फ़ॉन्ट (Noto Sans Masaram Gondi) | pill के status/badge text पर वही font family लगाना (वरना visual mismatch) |
| toolbar elements / access points | unchanged — pill सिर्फ़ एक **नया** view-tree है |
| theme colors | pill का background मौजूदा theme manager से लो (अलग hardcoded color नहीं) |

**Regression गारंटी के लिए:** implement शुरू करने से पहले मौजूदा behavior के 5 manual tests नोट कर लो
(Gondi conversion के test sentences, nukta cases, backspace timing, layout screenshot, font) — और हर phase के बाद
उन्हीं को दोबारा चलाओ। पूरी list `RESEARCH.md §13` के "★ Regression" भाग में है।

---

## 🎯 एक-पंक्ती में निष्कर्ष

तुम्हारा spec **~90% Gboard-exact** है। तीन जगह फ़र्क़ है:
1. **§5 overlay** — Gboard overlay use ही नहीं करता; IME-window + touch-region pass-through का trick है *(विकल्प चुनना होगा)*
2. **"बोल रहा हूं"** — Gboard लिखता है **"अब बोलें"**
3. **drag → auto-flip** — Gboard में explicit menu-item flip पक्का है, drag-auto-flip तुम्हारा बेहतर UX addition है

बाकी सब (states, order, sticky pause, menu items, symbols categories, vertical pill, keyboard-icon dismiss,
dp-based sizing, drag persistence) — **हूबहू मिलता है**।
