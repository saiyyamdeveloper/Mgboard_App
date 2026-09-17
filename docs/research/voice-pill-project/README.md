# 🎙️ voice-pill-project  (status: ✅ IMPLEMENTED — Mgboard `voice/` package mein)

> **Implementation note (बाद में जोड़ा गया):** यह research अब Mgboard app में लागू हो
> चुकी है — देखो `IMPLEMENTATION.md` (इसी folder में) और
> `app/src/main/kotlin/com/mgboard/keyboard/voice/`. Owner के चारों decisions:
> (A) IME-window widget · "अब बोलें" (Gboard-exact) · पूरा 8-item menu · offline-preferred
> recognition. 219 JVM assertions पास।

**क्या है:** MgBoard के लिए *Gboard-style multi-state Voice Pill* (Assistant Voice Typing toolbar) —
पूरा deep research, **असली Gboard APK (18.3.1-beta, Sep 2026) की forensic evidence** के साथ।

**Status:** सिर्फ़ research + spec save है। जब owner कहेगा **"voice pill project implement karo"**,
तब इसे MgBoard codebase में बनाना है।

---

## Files

| File | काम |
|---|---|
| `README.md` | यही file — quick map + वो 3 decisions जो implement से पहले लेने हैं |
| `RESEARCH.md` | ⭐ मुख्य document — Gboard में यह कैसे काम करता है (APK evidence), पूरा logic/state-machine/function breakdown, 5 states का exact mapping, drag & orientation-flip का असली mechanism, recognition engine, animation spec, **overlay requirement की reality check**, MgBoard के लिए recommended architecture, test matrix |
| `APK-EVIDENCE.md` | Gboard APK से निकाला गया कच्चा सबूत: permissions, manifest components, 60+ exact strings (English + **Hindi**), internal flag names, pref keys, theme element names |
| `SPEC-vs-GBOARD.md` | तुम्हारा Final Prompt ↔ Gboard का असली behavior — line-by-line diff (क्या हूबहू है, क्या अलग है, क्या technically संभव नहीं) |

---

## 30-सेकंड में सबसे ज़रूरी निष्कर्ष

1. **Gboard इसे "pill" नहीं, "voice toolbar" / "widget" कहता है** — असली strings:
   `Switch to vertical toolbar`, `widget_change_widget_orientation`, `widget_x_position`, `widget_y_position`.
2. **Gboard के पास `SYSTEM_ALERT_WINDOW` permission है ही नहीं** (18.3.1-beta manifest से verified)।
   → मतलब pill कोई "display over other apps" overlay **नहीं** है।
3. pill असल में **वही IME window है** — collapsed state में, drag करने लायक, और
   `InputMethodService.onComputeInsets()` के `TOUCHABLE_INSETS_*` से बाकी screen पर tap **pass-through** होता है।
4. इसलिए तुम्हारे prompt का **§5 (system-wide floating overlay) Gboard जैसा नहीं बन सकता** —
   उसके लिए overlay permission माँगनी पड़ेगी, जो Gboard माँगता नहीं। तीन विकल्प `RESEARCH.md §9` में हैं।
5. Recognition engine: **NGA** (`res/NGA.xml`), on-device (`enable_ondevice_voice`,
   `dictation_feature_split.apk` → `libdictation_jni.so`), + `dictation_type_jetson` / `dictation_type_traditional`.
6. Drag + edge-flip असली है: `"Hold and drag to move toolbar"`, `"Force the toolbar in horizontal mode and disable dragging"`,
   `VOICE_enable_vertical_widget[_landscape|_foldable|_foldable_landscape]`.
7. Position **persist** होता है: `widget_x_position` / `widget_y_position` prefs
   (`REGEX|.*widget_x_position_.+` → per-keyboard/locale scope)।

---

## Implement शुरू करने से पहले यह 3 decisions ज़रूरी हैं

| # | Decision | विकल्प | असर |
|---|---|---|---|
| 1 | **Window strategy** | (A) IME-window के अंदर widget = **Gboard जैसा, 0 permission** · (B) `TYPE_APPLICATION_OVERLAY` = सच में हर app पर तैरेगा, पर "Display over other apps" माँगना पड़ेगा · (C) hybrid | सबसे बड़ा architectural फ़ैसला — बाकी सब इसी पर टिका है |
| 2 | **Pill कहाँ तक टिके** | IME window तब मरता है जब input-focus जाता है / user back दबाता है। "session भर persistent" की हद यही है | Gboard भी यही करता है (mode pref में याद रखकर अगली बार restore) |
| 3 | **Language** | Kotlin या Java + UI: custom View / Compose | दोनों के skeleton `RESEARCH.md §10` में हैं |

बाकी छोटे decisions (min drag threshold, flip threshold 24–32dp, animation 150–250ms, backspace 400/70ms)
prompt में पहले से confirmed हैं।

---

## ⚠️ Implement करते समय सबसे बड़ा नियम (owner का खुद का)

MgBoard के मौजूदा typing/conversion behaviors — **Devanagari→Gondi converter, nukta नियम,
hold-backspace timing (400ms/70ms), मौजूदा keyboard layout, keys, फ़ॉन्ट (Noto Sans Masaram Gondi),
toolbar elements** — पूरी तरह **unchanged** रहेंगे।
Voice pill एक **नया, additive** module है: मौजूदा `KeyboardView` / key-event pipeline को छूना ही नहीं है।
Backspace-repeat और text-insertion वही मौजूदा helper reuse करेगा (दोबारा implement नहीं करेंगे)।
