# Translate panel + GIF/Stickers — web research (2026-09-17)

> **Status: ✅ IMPLEMENTED** (user-confirmed decisions: ML Kit on-device primary;
> bundled sticker pack + Klipy search with placeholder key; translate pehle).
> Code: `translate/` + `media/` + `ui/TranslatePanel.kt` + `ui/MediaPanels.kt` +
> `ime/MlKitTranslateEngine.kt` + `media/MediaCommitController.kt`.
> Tests: `TranslateTests.kt` + `MediaTests.kt` (136 + 87 assertions).

User ke do sawaal:
1. **Translate panel** Hindi↔English ho sakta hai kya?
2. **GIF / Stickers tabs** — check karke batao kya mila.

Neeche sirf verified sources ke facts hain, phir MgBoard-specific plan.

---

## PART 1 — Translate: **haan, ho sakta hai. On-device, bina internet, bina API key.**

### 1.1 ML Kit Translate (standalone, Firebase nahi chahiye)

| Fact | Detail | Source |
|---|---|---|
| Dependency | `implementation 'com.google.mlkit:translate:17.0.3'` | ML Kit Android guide |
| Supported languages | **59** languages, BCP-47 codes | ML Kit translation-language-support |
| **Hindi support** | ✅ `hi` — Hindi **officially listed** | ML Kit translation-language-support |
| **English support** | ✅ `en` | same |
| Also listed (India-relevant) | `bn` Bengali, `gu` Gujarati, `kn` Kannada, `mr` Marathi, `ta` Tamil, `te` Telugu, `ur` Urdu, `pa` ❌ (Punjabi **nahi** hai translate list mein) | same |
| Model size | **~30 MB per language** ( kuch sources 60–90 MB bolte hain — actual download per-language ~30MB) | Capawesome ML Kit plugin docs |
| Offline | ✅ poora translation **device par** hota hai; model ek baar download, phir internet nahi chahiye | ML Kit guide |
| Permissions | **koi nahi** (INTERNET sirf model download ke liye) | ML Kit guide |
| Language detection | `LanguageIdentification` API se source detect, `TranslateLanguage.fromLanguageTag()` se convert | ML Kit guide |
| Pivot rule | ML Kit **English ke through** translate karta hai. Isliye **hi→en aur en→hi direct hain** (best case); hi→mr jaisa pair English pivot se jaata hai (quality thodi kam) | ML Kit / HackerNoon |
| API shape | `TranslatorOptions.Builder().setSourceLanguage(ENGLISH).setTargetLanguage(HINDI)` → `Translation.getClient(options)` → `downloadModelIfNeeded(conditions)` → `translate(text)` (async `Task`) | ML Kit guide |
| Lifecycle | `Translator.close()` karna zaroori hai (warna resource leak) | ML Kit guide |
| Cost | **Free** | ML Kit guide |

**Nateeja:** MgBoard ke liye yeh perfect fit hai — zero permission, on-device, hi↔en
officially supported. Model download Wi-Fi par karna recommended (`DownloadConditions.requireWifi()`).

### 1.2 Gboard khud kaise karta hai (comparison)

- Gboard ka translate **Google Translate ki web service** use karta hai → **internet zaroori**.
  *"your mobile's data connection need to be active… it uses Google's web translation services"*
  (keyboardapps.net). Battery-saver mode mein fail hota hai.
- 100+ languages, source auto-detect, top-left/top-right language pickers + beech mein **swap** icon
  (businessinsider how-to).
- Pixel par offline bhi hota hai — par woh **Android System Intelligence** ke downloaded language
  packs se (Reddit r/GooglePixel), yaani Google ke apne system component se, third-party ke liye available nahi.

**Matlab:** MgBoard ML Kit se *Gboard-jaisa UI* bana sakta hai, aur usse **behtar privacy** ke saath
(text device se bahar jaata hi nahi). Yeh MgBoard ke "zero-cloud" stance ke saath match karta hai.

### 1.3 Jo options reject kiye

| Option | Kyun reject |
|---|---|
| Google Cloud Translation API | API key + **billing account** + text cloud par jaata hai |
| `translate.googleapis.com/translate_a/single` (unofficial endpoint) | koi key nahi chahiye par **unofficial/ToS-violating**, kabhi bhi band ho sakta hai (Tenor ke saath jo hua wahi) |
| Lingva Translate (open-source Google proxy) | third-party public instances, bharosa nahi, rate limits |
| LibreTranslate | self-host chahiye; public instances limited |
| **MyMemory** | free, key nahi chahiye — par **anonymous 5,000 chars/day**, `q` param **max 500 bytes**, quality crowdsourced (inconsistent). Sirf *optional online fallback* ke roop mein theek, primary nahi |

### 1.4 Hinglish ka honest limit

ML Kit language-ID `hi` ko **Devanagari** script maanta hai (ML Kit language-id table mein
`hi` = Devanagari, `hi-Latn` = Latin — par **translate** list mein sirf `hi` hai).
Isliye **romanized Hinglish** ("kaise ho bhai") ko ML Kit English ya `und` detect karega.
Plan: panel mein **manual source language** selector rakho (Gboard bhi rakhta hai) — user
`हिन्दी` chune to Devanagari text translate hoga. Romanized-Hindi→English ke liye alag
transliteration+translation pipeline chahiye hoga (MgBoard ke paas Devanagari↔Roman engine
pehle se hai — isko future mein jod sakte hain: **Hinglish → Devanagari → ML Kit → English**).
Yeh ek genuinely naya feature ban sakta hai jo Gboard ke paas bhi nahi hai.

---

## PART 2 — GIF / Stickers: **yahan badi khabar mili. Tenor khatam ho gaya.**

### 2.1 🚨 Gboard ka GIF source (Tenor) third parties ke liye BAND

| Date | Event |
|---|---|
| 2018 | Google ne **Tenor acquire** kiya — Gboard/Google Messages/Chat ke GIF ke liye |
| **13 Jan 2026** | Naye API key sign-ups + naye integrations **block** |
| **30 Jun 2026** | Tenor API **poori tarah shut down**; saare API/Ads Distribution Agreements terminate |
| Aaj (2026-09-17) | Tenor.com + GIF Keyboard app + **Gboard** chalte hain; **third-party API access khatam** |

Sources: Hacker News "The Google Tenor GIF API has been shut down" (Jan 2026),
gigazine.net (Jul 2026), techgenyz.com (Jul 2026), Ars Technica, Klipy migration guides.

Affected: X/Twitter (migrated 20 Jun), Discord, WhatsApp, Bluesky.

**MgBoard ke liye matlab:** "Gboard jaisa GIF tab Tenor se bana lenge" — yeh raasta **ab exist hi nahi karta**.
Koi bhi naya keyboard Gboard ka exact GIF backend use nahi kar sakta.

### 2.2 Ab jo options bache hain

#### (a) Klipy — sabse strong candidate

| Fact | Detail |
|---|---|
| Founder | **Frank Nawabi** — jinhone Tenor banaya tha aur Google ko becha. Team ex-Tenor |
| Investors | $3.8M raise, **Google among the investors** |
| Already used by | WhatsApp (Tenor → Klipy migrate kar chuka), Discord (testing), Canva, Figma, Miro, Microsoft Outlook, Bluesky (in progress), Baidu, KiKa |
| Price | **Free for life**, no usage caps. Monetization = optional ads (opt-in, rev share) |
| Content types | GIF, **Sticker**, Clip (video), Meme, **AI Emoji** — 5 alag APIs |
| Testing-mode limit | **100 API requests/hour** jab tak key "Testing" mode mein hai; Production access request karna padta hai → unlimited |
| Base URL | `https://api.klipy.com` |
| Endpoints | `/api/v1/{app_key}/gifs/trending`, `/gifs/search?q=`, `/gifs/categories`, `/gifs/recent/{customer_id}`, `/gifs/items?slugs=`, `POST /gifs/share/{slug}`, `POST /gifs/report/{slug}` — stickers/clips/memes ke liye same shape |
| Params | `page`, `per_page` (**min 8, max 50, default 24**), `q`, `customer_id` (per-user consistent id), `locale` (ISO 3166-1 alpha-2 → **IN possible**), `format_filter` (gif,webp,jpg,mp4,webm), `content_filter` (off/low/medium/high) |
| Response | `result`, `data.data[]` → `id`, `slug`, `title`, `file.{hd,md,sm,xs}.{gif,webp,jpg,mp4,webm}.{url,width,height,size}`, `tags`, `type`, `blur_preview` (base64 placeholder) |
| Media domains | `static.klipy.com`, `static1.`, `static2.` |
| Compliance | ISO 27001, GDPR, SOC 2 Type II |
| **Attribution** | **KLIPY branding UI mein dikhani zaroori** (Step 2 of onboarding) |

**Klipy ke integration rules jo hamare design par asar daalte hain** (docs.klipy.com/integration-requirements):

1. URLs/tracking params **alter nahi kar sakte**
2. Media **directly Klipy URLs se load** karna hai — *mirror/re-host/store nahi* (written approval ke bina)
3. Requests **end-user client se** jaane chahiye — proxy/server nahi
4. Search/Trending results ka **order change nahi** kar sakte
5. Klipy content **alag grid/tab** mein hona chahiye (doosre provider ke saath mix nahi)
6. Content filtering Klipy **Partner Panel** se configure hoti hai

⚠️ **Conflict jo pehle solve karna hoga:** Android par keyboard se GIF bhejne ke liye file
**local disk par** chahiye (FileProvider URI → `commitContent`). Rule #2 kehta hai media store/mat karo.
Iska matlab: ya to (i) Klipy se **written approval** lein (`developers@klipy.com` — unka doc khud
kehta hai custom caching ke liye approval lein), ya (ii) sirf **preview** direct URL se load karein aur
insert ke waqt ek **transient temp file** banayein jo turant delete ho jaye (temporary delivery buffer,
cache nahi). Option (ii) technically defensible hai par **email karke confirm karna chahiye**.

#### (b) Giphy — mehenga ho gaya

- Meta se **Shutterstock** ke paas gaya; ab **production API paid** (ek developer ko **$70K/year** quote mila — Reddit)
- Free **beta key = 100 requests/hour**, production ke liye application + pricing
- **Mandatory** "Powered By GIPHY" attribution (ToS §5A)
- Stickers alag library hai (`/stickers/search`, transparent animated GIFs)
- Sabse bada catalog, par indie/open-source keyboard ke liye **cost + approval risk**

#### (c) Imgur API
Free (1,250 uploads/day, 12,500 requests/day) par **community content** hai — curated reaction-GIF search nahi. Keyboard ke liye weak fit.

#### (d) Apna bundled sticker pack (zero API) ✅
Gboard ke Stickers tab mein third-party packs aate hain (Bitmoji, sticker.ly). Hum **apne assets**
bundle kar sakte hain — 12–24 stickers (WebP), offline, no key, no attribution, no rate limit.
MgBoard ke Gondi/Devanagari theme ke saath consistent. Yeh tab **turant, bina kisi external dependency** kaam karega.

### 2.3 Insertion ka technical mechanism (yeh provider-independent hai)

Android ka **Commit Content API** (API 25+, `androidx.core` ke `InputConnectionCompat` se API 24 par bhi graceful):

```
1. Editor (app) batata hai:  EditorInfo.contentMimeTypes  (e.g. ["image/gif"])
2. IME padhta hai:           EditorInfoCompat.getContentMimeTypes(info)
3. IME bhejta hai:           InputConnectionCompat.commitContent(ic, editorInfo,
                               InputContentInfoCompat(uri, ClipDescription(desc, ["image/gif"])),
                               INPUT_CONTENT_GRANT_READ_URI_PERMISSION, null)
```

Rules:
- Media **FileProvider** se `content://` URI hona chahiye (IME ke apne package ka), network URI nahi
- **Composing text ke waqt `commitContent()` mat call karo** — editor focus kho sakta hai
- API 25+ par `INPUT_CONTENT_GRANT_READ_URI_PERMISSION` flag lagao

🚨 **Sabse important practical baat:** **editor ko opt-in karna padta hai.**
Jis app/field ne `contentMimeTypes` declare nahi kiya, wahan GIF **jaata hi nahi**.
Gboard khud yahi toast dikhata hai: **"The text field does not support GIF insertion from the keyboard"**
(Stack Overflow, 2018). WhatsApp/Telegram/Discord/Gmail/Google Messages support karte hain;
zyada-tar plain `EditText`, browser address bars, aur password fields **nahi**.

Isliye MgBoard ko:
- `getContentMimeTypes()` check karke **GIF/Stickers tabs ko enable/disable** karna chahiye
- Unsupported field par Gboard ka **verbatim message** dikhana chahiye
- Fallback offer karna chahiye: **Copy image / Share** (Gboard bhi GIF par share option deta hai)

### 2.4 Rendering dependency

Compose mein animated GIF dikhane ke liye: **Coil** `io.coil-kt:coil-compose:2.7.0` +
`coil-gif` (GIF decoder). Ya `ImageDecoder`/`AnimatedImageDrawable` (API 28+) manually.
Coil sabse aasaan hai, ~1 MB.

---

## PART 3 — MgBoard par kya ban sakta hai (concrete plan)

### 3.1 Translate panel (hi ↔ en) — **high confidence, low risk**

```
translate/TranslateModel.kt        ← PURE Kotlin (JVM testable)
  TranslateLang (HI/EN + code + native name + en name)
  TranslateState (source, target, detected, input, output, phase)
  phase: IDLE | DETECTING | MODEL_DOWNLOADING(progress) | TRANSLATING | READY | ERROR(reason)
  swap(), setSource(), setTarget(), onInputChanged() [300ms debounce]
  canTranslate(text) — Devanagari vs Latin heuristic (Hinglish warning)
TranslateEngine (seam interface)   ← ML Kit implementation behind it
  fun ensureModel(src,tgt, onProgress) ; fun translate(text, cb) ; fun close()
ui/TranslatePanel.kt               ← Gboard-exact layout
  top: [source ▾]  ⇄  [target ▾]     beech mein swap
  middle: translated text (scrollable)
  bottom-right: ✓ (insert translated text)
  footer: "On-device · text device se bahar nahi jaata" badge
ime/MgBoardIme.kt                  ← Translator lifecycle + insert
```

- **hi→en aur en→hi** ML Kit mein direct hain → quality sabse achhi isi pair ki hogi (jo user ne maanga)
- Model download: Wi-Fi-only default, Settings mein toggle + size warning (~30 MB × 2)
- `Translator.close()` on `onFinishInputView`
- Gated reason tabhi dikhe jab model download fail ho ya ML Kit unavailable ho — warna panel live
- Bonus: **Hinglish → Devanagari (MgBoard ka apna engine) → ML Kit → English** pipeline future mein

### 3.2 GIF / Stickers tabs — **3 tiers, honest gating ke saath**

| Tier | Kya | External dependency | Kab |
|---|---|---|---|
| **1. Stickers (bundled)** | apna 12–24 sticker pack, `res/raw` + manifest, categories, recents | **kuch nahi** | turant |
| **2. GIF/Sticker search (Klipy)** | trending + search + categories + recents, `commitContent` insertion | Klipy **free API key** + attribution + caching clarification email | key milne par |
| **3. Giphy fallback** | same, biggest catalog | paid production key (risk) | sirf zaroorat pade to |

Dono tiers ke liye **ek hi insertion pipeline** (`MediaCommitController`) — provider swap
sirf `ContentProvider` interface se hota hai (Klipy/Giphy/Bundled implementations).

Gating (hide-nothing):
- Editor support nahi → tab dikhta hai, disabled state + Gboard verbatim: *"The text field does not support GIF insertion from the keyboard"*
- API key configure nahi → panel khulta hai, setup hint + bundled stickers kaam karte rehte hain
- Internet nahi → *"Can't use this tool at the moment. Please try again later."* (Gboard verbatim)

Privacy note (keyboard ke liye important): GIF search ka **query third party ko jaata hai** →
Settings mein clear disclosure + default OFF rakha ja sakta hai.

---

## Sources

**Translate**
- ML Kit Translate Android guide — `developers.google.com/ml-kit/language/translation/android` (dependency `com.google.mlkit:translate:17.0.3`, TranslatorOptions/downloadModelIfNeeded/translate, `Translator.close()`, model Wi-Fi download recommendation)
- ML Kit translation supported languages — `developers.google.com/ml-kit/language/translation/translation-language-support` (**59 languages, `hi` Hindi + `en` English listed**, updated 2026-09-16)
- ML Kit language identification supported languages — `.../language/identification/langid-support` (`hi` Devanagari, `hi-Latn` Latin)
- Capawesome Capacitor ML Kit Translation — models **~30 MB**, offline translation, `deleteDownloadedModel`
- HackerNoon ML Kit guide — *"designed to translate to and from English… English will be used as an intermediate language"*, *"completely free and works offline"*
- keyboardapps.net Gboard translation — Gboard uses **Google's web translation service**, needs data connection, fails in battery saver
- businessinsider Gboard translate how-to — language pickers top-left/top-right + **swap icon in middle** + ✓ to insert
- Reddit r/GooglePixel — Pixel offline translate = **Android System Intelligence** language packs
- MyMemory usage limits — `mymemory.translated.net/doc/usagelimits.php` (anonymous **5,000 chars/day**, email → 50,000, whitelist → 150,000; `q` max 500 bytes)

**GIF / Stickers**
- Hacker News: "The Google Tenor GIF API has been shut down" (Jan 2026) — deprecation letter: sign-ups blocked **13 Jan 2026**, agreements terminated **30 Jun 2026**
- gigazine.net (1 Jul 2026) + techgenyz.com (2 Jul 2026) — Tenor API discontinued; Tenor.com/GIF Keyboard/**Gboard**/Chat/Messages unaffected
- Interesting Engineering on X (Jul 2026) — Klipy raised **$3.8M with Google among investors**; co-founder **Frank Nawabi founded Tenor**; X migrated 20 Jun
- iroiro.us / agentdeals.dev migration guides — **GIPHY no longer free** (Shutterstock), beta key **100 req/hr**, Imgur free but community content, Klipy free-for-life
- Klipy docs — `docs.klipy.com`: getting-started (Partner Panel key, testing = **100 req/hr**, **attribution required**, production = unlimited), integration-requirements (no proxy, no mirror/cache without written approval, preserve order, dedicated grid), network-requirements (`api.klipy.com`, `static{,1,2}.klipy.com`), gifs-search-api (params `page`/`per_page` 8–50/`q`/`customer_id`/`locale`/`format_filter`/`content_filter`; response `file.{hd,md,sm,xs}.{gif,webp,jpg,mp4,webm}` + `blur_preview`), sticker/clip/meme/AI-emoji APIs
- Giphy developer docs + support — beta key 100 calls/hr, production requires application + **"Powered By GIPHY"** attribution (ToS §5A), sticker endpoints
- Android Developers: **Image keyboard support** — `developer.android.com/develop/ui/views/touch-and-input/image-keyboard` (Commit Content API, API 25+, `EditorInfo.contentMimeTypes`, `commitContent()`, v13/androidx support library, don't call during composing text)
- Stack Overflow "Gboard: Enable GIF insertion on EditText" — Gboard's toast *"The text field does not support GIF insertion from the keyboard"*; `InputConnectionCompat.commitContent` + `INPUT_CONTENT_GRANT_READ_URI_PERMISSION` code
