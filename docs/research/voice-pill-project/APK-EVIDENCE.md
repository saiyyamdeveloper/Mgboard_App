# 📦 Gboard APK — Raw Forensic Evidence

**Sample:** `Gboard 18.3.1.977415014-beta-arm64-v8a`
**Package:** `com.google.android.inputmethod.latin` · **versionCode:** 176004238 · **built:** 2026-09-14
**minSdk:** 26 · **targetSdk:** 37 · **compileSdk:** 37 (DEV codename) · `coreApp="true"`
**`<original-package android:name="com.android.inputmethod.latin"/>`**
**Source:** APKCombo → Cloudflare R2 (`apks.…r2.cloudflarestorage.com/…/18.3.1.977415014-beta-arm64-v8a/…`)
**Tools:** `pyaxmlparser` (binary AXML) + custom `resources.arsc` string-pool parser → **220,375 strings**

---

## 1. Bundle contents (XAPK / `.apks`)

```
 79,794,113  com.google.android.inputmethod.latin.apk     ← base
 35,533,030  brella_feature_split.apk
 11,292,906  tenoranimation_feature_split.apk
  4,112,613  dictation_feature_split.apk                  ← ★ voice/dictation module
    162,522  config.xxxhdpi.apk   (+ xxhdpi/xhdpi/hdpi/tvdpi/mdpi/ldpi)
      3,346  manifest.json
```

### `dictation_feature_split.apk` — पूरा content
```
      2,768  AndroidManifest.xml
  4,080,904  lib/arm64-v8a/libdictation_jni.so            ← ★ on-device dictation engine (JNI)
         32  stamp-cert-sha256
```
> **निष्कर्ष:** dictation एक **Play Feature Delivery split** है — base APK में engine नहीं,
> ज़रूरत पड़ने पर download होता है। और engine **native (C++)** है, Java नहीं।

---

## 2. Permissions (पूरी list — verbatim)

```
android.permission.ACCESS_NETWORK_STATE
android.permission.ACCESS_WIFI_STATE
android.permission.FOREGROUND_SERVICE
android.permission.GET_ACCOUNTS
android.permission.GET_PACKAGE_SIZE
android.permission.INJECT_KEY_EVENTS
android.permission.INTERNET
android.permission.PERSONAL_CONTEXT_HOST_INSIGHT_SURFACE
android.permission.PERSONAL_CONTEXT_PUBLISH_HINTS
android.permission.PERSONAL_CONTEXT_RECEIVE_INSIGHTS
android.permission.READ_CONTACTS
android.permission.READ_USER_DICTIONARY
android.permission.RECEIVE_BOOT_COMPLETED
android.permission.VIBRATE
android.permission.WAKE_LOCK
android.permission.WRITE_USER_DICTIONARY
com.google.android.apps.aicore.service.BIND_SERVICE
com.google.android.providers.gsf.permission.READ_GSERVICES
com.google.android.setupwizard.READ_DEVICE_ORIGIN_FIRST_PARTY
```

### ❌ जो नहीं हैं (और यही सबसे बड़ा सबूत है)
| नहीं है | नतीजा |
|---|---|
| `SYSTEM_ALERT_WINDOW` | voice pill **कोई system overlay नहीं** — IME window के अंदर ही है |
| `RECORD_AUDIO` | Gboard खुद mic capture नहीं करता; `SpeechRecognizer` का recognition service (Google app) अपना mic use करता है |
| `FOREGROUND_SERVICE_MICROPHONE` | कोई mic foreground service नहीं → pill हमेशा "while-in-use" (IME visible) context में चलती है |
| `CAMERA`, `POST_NOTIFICATIONS` | irrelevant |

---

## 3. Manifest components (सारे windows/services)

```xml
<service android:name="com.android.inputmethod.latin.LatinIME"
         android:permission="android.permission.BIND_INPUT_METHOD"
         android:exported="true" android:directBootAware="true">      ← इकलौता IME

<service android:name="com.android.inputmethod.latin.spellcheck.AndroidSpellCheckerService"
         android:permission="android.permission.BIND_TEXT_SERVICE" android:exported="true"/>

<service android:name="com.google.android.apps.inputmethod.libs.dataservice.superpacks.SuperpacksForegroundTaskService"
         android:exported="false" android:foregroundServiceType="0x00000800"/>   <!-- specialUse: language packs -->
<service android:name="...SuperpacksBackgroundJobService"
         android:permission="android.permission.BIND_JOB_SERVICE" android:exported="false"/>

<service android:name="com.google.android.libraries.phenotype.registration.PhenotypeMetadataHolderService"
         android:enabled="false" android:process=""/>                    <!-- feature flags / experiments -->
<service android:name="com.google.android.gms.learning.internal.training.InAppTrainingService"
         android:process=":train"/>

<activity ... LauncherActivity/>            <activity ... LatinFirstRunActivity/>
<activity ... SettingsActivity/>            <activity ... LatinSpellCheckerSettingsActivity/>
<activity ... StylusSettingsActivity/>      <activity ... SharingLinkReceiveActivity/>
<activity ... LinkReceivingLauncherActivity/>   <activity ... ImageFeedbackActivity/>

<provider ... MainProcessInitializationProvider/>   <provider ... TrainProcessInitializationProvider/>
<provider ... SettingsSearchIndexablesProvider/>    <provider ... ClipboardContentProvider/>
<receiver ... ApkUpdatedReceiver/>                  <receiver ... InputDeviceReceiver/>
```
> **कोई overlay/floating-window service नहीं है।** सारा voice toolbar `LatinIME` के अंदर रहता है।

---

## 4. Internal identifiers — असली vocabulary

### 4.1 Action IDs (बटन/मेनू items)
```
widget_hide_keyboard                  ← ⌨ keyboard icon (pill → full keyboard)
widget_keyboard
widget_delete                         ← ⌫ backspace
widget_ime_action                     ← editor action (send/go/next)
widget_change_widget_orientation      ← ⟲ horizontal ↔ vertical
widget_more_access_points             ← "…" / hamburger
widget_access_point_settings          ← ⚙
widget_access_point_stylus_gestures
widget_enable_markup
widget_x_position                     ← ★ drag position persist
widget_y_position                     ← ★
```

### 4.2 Preference keys / flags
```
has_shown_voice_toolbar              voice_toolbar_shown_count
voice_toolbar_onboarding             voice_use_time
last_voice_toolbar_dictate_time      last_voice_dictate_time
opt_out_from_voice_toolbar           opt_out_from_stt_toolbar
seamless_voice_typing                enable_global_direct_to_dictation
enable_voice_input                   enable_ondevice_voice
enable_enhanced_voice_typing
enable_enhanced_voice_typing_auto_punctuation
enable_enhanced_voice_typing_automatic_language_switching
enable_enhanced_voice_typing_speech_enhancement
enhanced_voice_typing_prefer_detect_language
dictation_type_traditional           dictation_type_jetson
dictation_debug_audio_dump           dictation_share_debug_audio
enable_always_show_dragging_toolbar_tooltip
enable_always_show_pk_toolbar_orientation_tooltip
disable_stylus_toolbar               setting_physical_keyboard_toolbar_category_key
pref_key_clear_agentic_dictation_promo_banner_history
pref_key_stop_dictation_after_sending_writing_tools_v2_prompts
voice_donation_promo_banner          voice_donation_promo_banner_clicked  enable_voice_donation
VOICE_WRITING_TOOLS_REPORTING_BUTTON_ID
```

### 4.3 Orientation feature-flags (per device-state!)
```
VOICE_enable_vertical_widget
VOICE_enable_vertical_widget_landscape
VOICE_enable_vertical_widget_foldable
VOICE_enable_vertical_widget_foldable_landscape
PK_enable_vertical_widget            (+ _landscape / _foldable / _foldable_landscape)   ← physical keyboard
STYLUS_enable_vertical_widget        (+ _landscape / _foldable / _foldable_landscape)   ← stylus
```

### 4.4 Scoping regex (prefs किस-किस scope में हैं)
```
REGEX|.*widget_x_position_.+
REGEX|.*widget_y_position_.+
```
> यानी position **per-keyboard / per-locale** scoped है — हर keyboard का अपना pill position।

### 4.5 Engine files
```
res/NGA.xml          res/NgA.xml        ← NGA = Next Generation Assistant (speech engine config)
lib/arm64-v8a/libdictation_jni.so       ← on-device dictation (feature split में)
```

---

## 5. Theme element names (Snygg-style) — UI code से बनता है, XML से नहीं

```
.widget-keyboard.keyboard-background.horizontal        ← horizontal pill bg
.widget-keyboard.keyboard-background.vertical          ← vertical pill bg
.widget-keyboard.keyboard-body-area
.widget-content-wrapper.with_background
.widget-content-wrapper.no-background
.widget-content-wrapper.entry-menu
.widget-popup-menu-item.non-linear-scale               ← menu row (Material non-linear scale)
.widget-popup-menu-entry-label
.widget-popup-menu-entry-end-icon.non-linear-scale     ← checkmark / trailing icon
.widget-popup-menu-entry-header-label
.widget-popup-menu-entry-shortcuts-key
.widget-tooltip
.widget-tooltip-icon
.widget-tooltip-label
.widget-tooltip-button.borderless
.widget-tooltip-button.neutral-with-border
.widget-tooltip-button.positive-with-border
.widget-item-background
.icon.on-widget-icon-background.item-ripple
.icon.item-ripple.widget-item-background
.icon.widget-popup-menu-entry-shortcuts-key
.keytop.for-candidate-key.for-widget
.label.item-ripple.widget-item-background
.label.secondary.text-size-very-tiny.for-widget        ← छोटा language tag
.widget.proactive-suggestions-holder-border
```

### Color selectors (res/color/)
```
m3_standard_toolbar_button_text_color_selector.xml
m3_standard_toolbar_icon_button_container_color_selector.xml
m3_standard_toolbar_icon_button_icon_color_selector.xml
m3_standard_toolbar_icon_button_ripple_color_selector.xml
m3_vibrant_toolbar_button_text_color_selector.xml
m3_vibrant_toolbar_icon_button_container_color_selector.xml
m3_vibrant_toolbar_icon_button_icon_color_selector.xml
m3_vibrant_toolbar_icon_button_ripple_color_selector.xml
widget_popup_menu_item_highlight_color.xml
```
> `.non-linear-scale` का बार-बार आना बताता है कि transitions में **Material non-linear (emphasized) easing**
> और scale-based motion use होता है — simple linear fade नहीं।

---

## 6. User-facing strings — English (verbatim)

### Status
```
Speak now            Paused            Listening…           Just speak naturally
```

### Controls / actions
```
Toolbar                                     Show toolbar
Collapse keyboard                           Minimize keyboard
Minimize toolbar and hide suggestions       Hide your keyboard while voice typing
Exit dictation mode                          Stop dictation
Exit floating keyboard                       Exiting floating keyboard
Hide keyboard                                Hide on-screen keyboard
Move keyboard position                       Open the keyboard anytime
Open the keyboard at any time
Hold and drag to move toolbar
Switch to vertical toolbar                   Switch to horizontal toolbar
Open more voice options                      More voice options opened
More voice options closed                    Close more voice options
Open more keyboard options                   More keyboard options opened / closed
Close more keyboard options
```

### Menu items
```
Settings        Show voice commands      Show clipboard      Show translate
Show emoji      All voice commands       Hide voice commands
Close voice command list                 Voice command list closed.
Unsupported voice command                Voice command to rephrase the text
Open the list of supported voice rewrite command options
Is the highlighted text a voice command? If so, tap or say “Apply”
```

### Symbols overlay
```
SYMBOLS      Symbols      Symbol keyboard      Symbols mode      Close button
Recent       Numbers      Brackets             Arrows            Mathematics      List
```

### Settings / descriptions
```
Use the toolbar for easy voice typing
Enable voice toolbar and start voice typing automatically when keyboard is shown
Force the horizontal toolbar
Force the toolbar in horizontal mode and disable dragging
Vertical offset of docked horizontal toolbar
An integer value in pixels used to move the docked horizontal toolbar down.
Autocorrect suggestions shown at the cursor when toolbar is minimized
Toggle on = Autocorrect suggestions are shown on the toolbar and at the cursor when toolbar is minimized
Toggle off = Autocorrect suggestions are shown on the toolbar
Emojis on toolbar
Show physical keyboard toolbar
Quickly switch from the toolbar to the keyboard. Just tap the keyboard icon.
To quickly find the toolbar, tap MINIMIZE minimize icon on your keyboard while voice typing
To quickly find the toolbar, tap Minimize on your keyboard while voice typing
See voice commands<br>and access settings.
Go to a text field to use dictation
Dictation type        Additional module (Dictation)
Assistant voice typing, NGA, Smart dictation
Faster voice typing, Offline dictation, Local speech
Use advanced voice commands to edit text, add emoji, and more
Educational tip about dictation in multiple languages.
Educational tip about long pressing the microphone to start voice dictation in locked mode.
Key code of dictation key
The key code to be fired when the user presses the dedicated dictation physical key on a physical keyboard.
Allowing access to contacts along with your microphone will make dictations more precise.
```

### Accessibility / announcements
```
Voice command list closed.        Close voice command list        Close edit menu
Close features menu               Close menu                      Close button
Delete the last word or a selected word
Delete words by gliding left from 'Delete' key
Select suggestion on the toolbar
Show a button on the keyboard toolbar to open emoji keyboard
```

---

## 7. ⭐ User-facing strings — Hindi (verbatim, APK से)

```
अब बोलें                                                          ← "Speak now"
रोकी गई                                                            ← "Paused"
बोली को लिखाई में बदलने की सुविधा रोकी गई                            ← "Voice typing paused"
टूलबार                                                              ← "Toolbar"
टूलबार दिखाएं                                                        ← "Show toolbar"
टूलबार को छोटा करें और सुझावों को छिपाएं                              ← "Minimize toolbar and hide suggestions"
वर्टिकल टूलबार पर स्विच करें                                          ← "Switch to vertical toolbar"
क्षैतिज                                                             ← "Horizontal"
क्लिपबोर्ड दिखाएं                                                     ← "Show clipboard"
अनुवाद दिखाएं                                                        ← "Show translate"
इमोजी दिखाएं                                                         ← "Show emoji"
सेटिंग                                                              ← "Settings"
चिह्न                                                               ← "Symbols"
टूलबार को एक जगह से दूसरी जगह ले जाने के लिए, उसे दबाकर खींचें और सही जगह पर छोड़ें   ← drag tooltip
टूलबार से कीबोर्ड पर तेज़ी से स्विच करें. इसके लिए, बस कीबोर्ड आइकॉन पर टैप करें.      ← keyboard icon hint
टूलबार खोजने के लिए, बोली को लिखाई में बदलने की सुविधा इस्तेमाल करने के दौरान,
    अपने कीबोर्ड पर छोटा करें पर टैप करें                              ← onboarding
बोलेर टाइप गरिएको टेक्स्ट पठाउनुहोस् … (voice-command related variants)
```

### ❗ ध्यान देने लायक
- Gboard Hindi में **"बोल रहा हूं" कभी नहीं लिखता** — 220,375 strings में इसका **0 match**।
  असली string है **"अब बोलें"**।
- Gboard **"वर्टिकल टूलबार"** (transliterated) use करता है, "लंबवत टूलबार" या "ऊर्ध्वाधर टूलबार" नहीं
  (दोनों के 0 matches)। यही तुम्हारे spec से match करता है ✔
- "चिह्न" 29 जगह मिलता है → symbols header के लिए confirmed ✔
- `रोकी गई` 2 जगह, `अब बोलें` 1 जगह, `वर्टिकल टूलबार पर स्विच करें` 1 जगह,
  `क्लिपबोर्ड दिखाएं` 1, `अनुवाद दिखाएं` 2, `इमोजी दिखाएं` 1

---

## 8. AOSP cross-reference (IME window mechanics)

`frameworks/base/core/java/android/inputmethodservice/InputMethodService.java` (android14-release)

```java
// ── window layout: IME window पूरी स्क्रीन तक जाती है ────────────────────────
lp.gravity = Gravity.BOTTOM;
lp.setFitInsetsTypes(statusBars() | navigationBars());
lp.setFitInsetsSides(Side.all() & ~Side.BOTTOM);          // ← bottom excluded
lp.receiveInsetsIgnoringZOrder = true;
window.setFlags(FLAG_LAYOUT_IN_SCREEN | FLAG_NOT_FOCUSABLE | FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS, …);

// ── Insets class (line ~1370-1450): touch pass-through का API ───────────────
public int contentTopInsets;      // app को resize/pan करने के लिए
public int visibleTopInsets;      // कितना ढक रहा है (बार-बार बदल सकता है)
public final Region touchableRegion;
public int touchableInsets;       // TOUCHABLE_INSETS_FRAME | _CONTENT | _VISIBLE | _REGION

// ── IME के लिए system-gesture exclusion (framework खुद insets पढ़ता है) ──────
private void setImeExclusionRect(int visibleTopInsets) {
    View rootView = mInputFrame.getRootView();
    Insets systemGesture = rootView.getRootWindowInsets().getInsets(Type.systemGestures());
    …
    rootView.setSystemGestureExclusionRects(exclusionRects);
}
```

`WindowInsets#getSystemGestureInsets()` की official doc से:
> *"the system will put a limit of 200dp on the vertical extent of the exclusions … The limit does not apply
> while the navigation bar is stickily hidden, **nor to the `android.inputmethodservice.InputMethodService`
> input method** and `CATEGORY_HOME` home activity."*

→ **IME एक privileged window type है:** पूरे स्क्रीन का canvas, gesture-exclusion की 200dp छूट,
और system bars के ऊपर z-order — यही कारण है कि Gboard को overlay permission की ज़रूरत ही नहीं पड़ी।
