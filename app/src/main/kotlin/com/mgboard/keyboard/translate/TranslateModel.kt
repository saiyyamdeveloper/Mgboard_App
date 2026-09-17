package com.mgboard.keyboard.translate

/**
 * Translate panel ka model — Gboard ke translate panel jaisa behaviour, par
 * **on-device** (ML Kit Translate). Research: `docs/research/toolbar-project/TRANSLATE-GIF-FEASIBILITY.md`
 *
 * Yeh file **pure Kotlin** hai (koi Android/Compose import nahi) taaki poora state
 * machine `./scripts/run_jvm_tests.sh` par test ho sake. ML Kit sirf [TranslateEngine]
 * interface ke peeche Android side par implement hota hai.
 *
 * Gboard ke translate panel ke elements (businessinsider how-to + APK strings):
 *  - top-left  = source language picker
 *  - top-right = target language picker
 *  - beech mein = **swap** icon (⇄) — languages interchange
 *  - neeche = translated text, bottom-right ✓ se insert
 *  - source "Detect language" par set ho sakta hai (auto-detect)
 */

// ══════════════════════════ languages ══════════════════════════

/**
 * ML Kit Translate ki official supported-language list mein se woh languages jo
 * MgBoard ke liye relevant hain (`hi` Hindi aur `en` English **officially listed** hain).
 * Codes ML Kit ke `TranslateLanguage.*` constants ke BCP-47 values hain.
 */
enum class TranslateLang(val code: String, val native: String, val enName: String) {
    HINDI("hi", "हिन्दी", "Hindi"),
    ENGLISH("en", "English", "English"),
    // ML Kit list mein maujood, India-relevant — future expansion ke liye ready
    BENGALI("bn", "বাংলা", "Bengali"),
    GUJARATI("gu", "ગુજરાતી", "Gujarati"),
    KANNADA("kn", "ಕನ್ನಡ", "Kannada"),
    MARATHI("mr", "मराठी", "Marathi"),
    TAMIL("ta", "தமிழ்", "Tamil"),
    TELUGU("te", "తెలుగు", "Telugu"),
    URDU("ur", "اردو", "Urdu"),
    ;

    fun label(hindi: Boolean): String = if (hindi) native else enName

    companion object {
        /** ML Kit `TranslateLanguage.fromLanguageTag()` jaisa lookup. */
        fun fromCode(code: String?): TranslateLang? = values().firstOrNull { it.code == code }

        /**
         * MgBoard ka primary use-case: **Hindi ↔ English**.
         * Gboard bhi device language se default chunta hai.
         */
        fun defaultPair(uiHindi: Boolean): Pair<TranslateLang, TranslateLang> =
            if (uiHindi) HINDI to ENGLISH else ENGLISH to HINDI
    }
}

// ══════════════════════════ phases / results ══════════════════════════

/** Translate panel ki state machine — UI isi se decide karti hai kya dikhana hai. */
enum class TranslatePhase {
    /** Panel khula hai par abhi kuch translate nahi ho raha. */
    IDLE,

    /** ML Kit model check ho raha hai (pehli baar). */
    CHECKING_MODEL,

    /** Model download ho raha hai — [TranslateSession.downloadProgress] dikhega. */
    DOWNLOADING,

    /** Model ready, translate chal raha hai. */
    TRANSLATING,

    /** Output taiyaar hai — ✓ se insert ho sakta hai. */
    READY,

    /** Kuch fail hua — [TranslateSession.error] ka reason dikhega. */
    ERROR,
}

/**
 * Download/readiness ki wajah se panel mein jo message aata hai.
 * Gboard-style verbatim reason (hide-nothing: cheez chhupti nahi, wajah batati hai).
 */
enum class TranslateError(val en: String, val hi: String) {
    NO_ENGINE(
        "Translation isn’t available on this device.",
        "इस डिवाइस पर अनुवाद उपलब्ध नहीं है.",
    ),
    ENGINE_UNAVAILABLE(
        "Can’t use this tool at the moment. Please try again later.",
        "अभी इस टूल का उपयोग नहीं किया जा सकता. कृपया बाद में फिर कोशिश करें.",
    ),
    DOWNLOAD_FAILED(
        "Couldn’t download the translation model. Check your connection and try again.",
        "अनुवाद मॉडल डाउनलोड नहीं हो सका. अपना कनेक्शन जांचें और फिर कोशिश करें.",
    ),
    TRANSLATE_FAILED(
        "Can’t use this tool at the moment. Please try again later.",
        "अभी इस टूल का उपयोग नहीं किया जा सकता. कृपया बाद में फिर कोशिश करें.",
    ),
    NO_TEXT("Type something to translate.", "अनुवाद करने के लिए कुछ लिखें."),
    UNSUPPORTED_SCRIPT(
        "Romanized Hindi isn’t supported yet — switch the keyboard to हिन्दी for best results.",
        "रोमन हिन्दी अभी supported नहीं है — बेहतर नतीजे के लिए कीबोर्ड को हिन्दी पर बदलें.",
    ),
    ;

    fun label(hindi: Boolean): String = if (hindi) hi else en
}

/** Script detection ka natija — Hinglish (romanized Hindi) ke liye honest warning. */
enum class ScriptKind { EMPTY, DEVANAGARI, LATIN, MIXED, OTHER }

/**
 * ML Kit ka pivot rule: ML Kit **English ke through** translate karta hai, isliye
 * hi→en aur en→hi **direct pair** hain (sabse achhi quality). Baaki pairs English
 * pivot se jaate hain — yeh flag UI par "via English" note dikhane ke liye hai.
 */
fun isDirectEnglishPair(src: TranslateLang, tgt: TranslateLang): Boolean =
    src == TranslateLang.ENGLISH || tgt == TranslateLang.ENGLISH

// ══════════════════════════ script detection ══════════════════════════

/**
 * Devanagari block = U+0900..U+097F. ML Kit language-ID `hi` ko **Devanagari** script
 * maanta hai (translate list mein sirf `hi` hai, `hi-Latn` nahi), isliye romanized
 * Hinglish detect karna zaroori hai — warna user ko silently galat natija milega.
 */
fun detectScript(text: String): ScriptKind {
    var dev = 0
    var lat = 0
    var other = 0
    for (ch in text) {
        val cat = ch.category
        when {
            ch.code in 0x0900..0x097F -> dev++             // Devanagari block
            ch in 'a'..'z' || ch in 'A'..'Z' -> lat++       // basic Latin letters
            ch.code in 0x00C0..0x024F && ch.isLetter() -> lat++   // Latin Extended
            // digits, spaces aur punctuation neutral hain (script decide nahi karte)
            ch.isDigit() -> Unit
            cat == CharCategory.SPACE_SEPARATOR -> Unit
            cat == CharCategory.DASH_PUNCTUATION ||
                cat == CharCategory.START_PUNCTUATION ||
                cat == CharCategory.END_PUNCTUATION ||
                cat == CharCategory.CONNECTOR_PUNCTUATION ||
                cat == CharCategory.OTHER_PUNCTUATION -> Unit
            // Kotlin `Char.isLetter()` supplementary chars ke surrogates par bhi true
            // deta hai (Gondi U+11D0C) — isliye high-surrogate letters ko neutral
            // maante hain; Gondi text translate panel ka case hai hi nahi.
            ch.isHighSurrogate() || ch.isLowSurrogate() -> Unit
            cat == CharCategory.CURRENCY_SYMBOL || cat == CharCategory.MATH_SYMBOL ||
                cat == CharCategory.MODIFIER_SYMBOL || cat == CharCategory.OTHER_SYMBOL -> other++
            else -> other++                                  // baaki non-Latin letters
        }
    }
    if (dev == 0 && lat == 0 && other == 0) return ScriptKind.EMPTY
    if (dev > 0 && lat > 0) return ScriptKind.MIXED
    if (dev > 0) return ScriptKind.DEVANAGARI
    if (lat > 0) return ScriptKind.LATIN
    return ScriptKind.OTHER
}

// ══════════════════════════ engine seam ══════════════════════════

/**
 * Translation engine ka seam. Android par iska implementation **ML Kit**
 * (`com.google.mlkit:translate`) se hota hai; JVM tests par fake se.
 *
 * ML Kit ke rules jo implementer ko follow karne hain:
 *  - `translate()` se pehle model **downloaded** hona chahiye (`downloadModelIfNeeded`)
 *  - download **Wi-Fi only** default (research: ~30 MB per language)
 *  - `Translator.close()` zaroori hai (resource leak warna)
 *  - ML Kit English-pivot hai → hi↔en direct, baaki via English
 */
interface TranslateEngine {
    /** Source + target ke liye model device par maujood hai kya. */
    fun isModelReady(src: TranslateLang, tgt: TranslateLang): Boolean

    /** Model download karo. `onProgress` 0f..1f. Finish par [onDownloaded]. */
    fun downloadModel(
        src: TranslateLang,
        tgt: TranslateLang,
        onProgress: (Float) -> Unit,
        onDownloaded: (Boolean) -> Unit,
    )

    /** Translate karo (async). Result [onResult] se — success par text, failure par null. */
    fun translate(src: TranslateLang, tgt: TranslateLang, text: String, onResult: (String?) -> Unit)

    /** ML Kit `Translator.close()` — panel band/IME finish par. */
    fun close()

    companion object {
        /** Ek language model ka approximate download size (research: ~30 MB). */
        const val MODEL_SIZE_MB = 30
    }
}

// ══════════════════════════ session state ══════════════════════════

/** Panel ki poori visible state — UI isi ko padhti hai. */
data class TranslateSession(
    var src: TranslateLang = TranslateLang.ENGLISH,
    var tgt: TranslateLang = TranslateLang.HINDI,
    /** Gboard: source picker mein "Detect language" option hota hai. */
    var autoDetect: Boolean = true,
    var detected: TranslateLang? = null,
    var input: String = "",
    var output: String = "",
    var phase: TranslatePhase = TranslatePhase.IDLE,
    var downloadProgress: Float = 0f,
    var error: TranslateError? = null,
    /** Model device par downloaded hai (UI download hint ke liye). */
    var modelReady: Boolean = false,
)

// ══════════════════════════ controller ══════════════════════════

/**
 * Translate panel ka controller — debounce, model management, aur stale-result
 * discard ka poora logic. Compose/Android se free, isliye JVM-testable.
 *
 * Gboard "translate as you type" karta hai — isliye input par [debounceMs] ka
 * debounce (300 ms). Timer injectable hai taaki tests deterministically chalein.
 *
 * @param scheduleTimer `(delayMs, runnable) -> token`; returning token 0 means "no timer".
 * @param cancelTimer `(token) -> Unit`
 */
class TranslateController(
    private val engineProvider: () -> TranslateEngine?,
    private val scheduleTimer: (Long, () -> Unit) -> Long = { _, r -> r(); 0L },
    private val cancelTimer: (Long) -> Unit = {},
    private val debounceMs: Long = DEBOUNCE_MS,
    /** Device ka UI Hindi mein hai kya (labels ke liye). */
    var uiHindi: Boolean = false,
) {
    var session = TranslateSession()
        private set

    /** UI ko refresh ka signal (Compose tick). */
    var onChanged: (() -> Unit)? = null

    /** ✓ tap → translated text editor mein insert ho. */
    var onInsert: ((String) -> Unit)? = null

    private var pendingTimer: Long = 0L
    private var requestToken: Int = 0

    // ── language pickers (Gboard: top-left / top-right) ──────────────────────

    fun setSource(lang: TranslateLang) {
        session.src = lang
        session.autoDetect = false
        retranslate()
    }

    fun setTarget(lang: TranslateLang) {
        session.tgt = lang
        retranslate()
    }

    /** Gboard ka "Detect language" source option. */
    fun setAutoDetect(enabled: Boolean) {
        session.autoDetect = enabled
        retranslate()
    }

    /**
     * ⇄ swap — Gboard: *"You can also tap the icon in the middle to switch the two
     * languages' positions."* Swap ke baad purana output naya input ban jaata hai
     (Gboard jaisa), taaki back-and-forth turant kaam kare.
     */
    fun swap() {
        val oldSrc = session.src
        val oldTgt = session.tgt
        val oldOutput = session.output
        session.src = oldTgt
        session.tgt = oldSrc
        session.detected = null
        // agar output maujood hai to use naya source text bana do (Gboard behaviour)
        if (oldOutput.isNotEmpty()) {
            session.input = oldOutput
            session.output = ""
        }
        // auto-detect ab naye source par laagu hoga
        retranslate()
    }

    // ── typing ──────────────────────────────────────────────────────────────

    /** User ne keyboard par type kiya — debounce ke baad translate. */
    fun onInput(text: String) {
        session.input = text
        session.output = ""
        session.error = null
        if (text.isEmpty()) {
            cancelPending()
            session.phase = TranslatePhase.IDLE
            session.downloadProgress = 0f
            bump()
            return
        }
        session.phase = TranslatePhase.TRANSLATING
        cancelPending()
        pendingTimer = scheduleTimer(debounceMs) { runTranslation() }
        bump()
    }

    /** Debounce skip karke turant translate (✓ se pehle / Enter par). */
    fun translateNow() {
        cancelPending()
        if (session.input.isEmpty()) return
        runTranslation()
    }

    /** ✓ (bottom-right check mark) — translated text insert karo. */
    fun insertOutput(): Boolean {
        val out = session.output
        if (out.isEmpty()) return false
        onInsert?.invoke(out)
        return true
    }

    // ── model management ────────────────────────────────────────────────────

    /**
     * Model download shuru karo. Wi-Fi-only policy host (IME) decide karta hai
     * (`DownloadConditions.requireWifi()`); controller sirf state track karta hai.
     */
    fun downloadModel() {
        val engine = engineProvider()
        if (engine == null) {
            session.phase = TranslatePhase.ERROR
            session.error = TranslateError.NO_ENGINE
            bump()
            return
        }
        if (engine.isModelReady(session.src, session.tgt)) {
            session.modelReady = true
            session.phase = if (session.input.isEmpty()) TranslatePhase.IDLE else TranslatePhase.TRANSLATING
            bump()
            if (session.input.isNotEmpty()) runTranslation()
            return
        }
        session.modelReady = false
        session.phase = TranslatePhase.DOWNLOADING
        session.downloadProgress = 0f
        session.error = null
        bump()
        engine.downloadModel(
            session.src,
            session.tgt,
            onProgress = { p ->
                session.downloadProgress = p.coerceIn(0f, 1f)
                bump()
            },
            onDownloaded = { ok ->
                session.modelReady = ok
                if (ok) {
                    session.downloadProgress = 1f
                    session.phase = if (session.input.isEmpty()) TranslatePhase.IDLE else TranslatePhase.TRANSLATING
                    bump()
                    if (session.input.isNotEmpty()) runTranslation()
                } else {
                    session.phase = TranslatePhase.ERROR
                    session.error = TranslateError.DOWNLOAD_FAILED
                    session.downloadProgress = 0f
                    bump()
                }
            },
        )
    }

    /** Engine se poochho ki model ready hai kya (panel khulte waqt). */
    fun refreshModelStatus() {
        val engine = engineProvider()
        if (engine == null) {
            session.modelReady = false
            if (session.phase != TranslatePhase.DOWNLOADING) {
                session.phase = if (session.input.isEmpty()) TranslatePhase.IDLE else TranslatePhase.ERROR
                if (session.input.isNotEmpty()) session.error = TranslateError.NO_ENGINE
            }
            bump()
            return
        }
        val ready = engine.isModelReady(session.src, session.tgt)
        session.modelReady = ready
        if (ready && session.phase == TranslatePhase.ERROR && session.error != TranslateError.TRANSLATE_FAILED) {
            session.phase = if (session.input.isEmpty()) TranslatePhase.IDLE else TranslatePhase.TRANSLATING
            session.error = null
        }
        bump()
    }

    /** ML Kit `Translator.close()` — panel band hone par. */
    fun release() {
        cancelPending()
        requestToken++          // koi bhi in-flight result stale ho jaayega
        engineProvider()?.close()
    }

    // ── internals ───────────────────────────────────────────────────────────

    private fun cancelPending() {
        if (pendingTimer != 0L) cancelTimer(pendingTimer)
        pendingTimer = 0L
    }

    private fun retranslate() {
        session.output = ""
        session.detected = null
        session.error = null
        // naye pair ka model chahiye ho sakta hai
        session.modelReady = engineProvider()?.isModelReady(session.src, session.tgt) ?: false
        if (session.input.isEmpty()) {
            session.phase = TranslatePhase.IDLE
            bump()
            return
        }
        session.phase = TranslatePhase.TRANSLATING
        cancelPending()
        pendingTimer = scheduleTimer(debounceMs) { runTranslation() }
        bump()
    }

    private fun runTranslation() {
        pendingTimer = 0L
        val text = session.input
        if (text.isEmpty()) {
            session.phase = TranslatePhase.IDLE
            session.error = null
            bump()
            return
        }

        // ── honest gating #1: engine hi nahi hai ────────────────────────────
        val engine = engineProvider()
        if (engine == null) {
            session.phase = TranslatePhase.ERROR
            session.error = TranslateError.NO_ENGINE
            bump()
            return
        }

        // ── honest gating #2: romanized Hindi (ML Kit Devanagari maanta hai) ─
        val script = detectScript(text)
        if (script == ScriptKind.LATIN && session.src == TranslateLang.HINDI) {
            session.phase = TranslatePhase.ERROR
            session.error = TranslateError.UNSUPPORTED_SCRIPT
            bump()
            return
        }
        // auto-detect: script se heuristically source decide karo
        val effectiveSrc = if (session.autoDetect) {
            when (script) {
                ScriptKind.DEVANAGARI, ScriptKind.MIXED -> TranslateLang.HINDI
                ScriptKind.LATIN, ScriptKind.OTHER -> TranslateLang.ENGLISH
                ScriptKind.EMPTY -> session.src
            }.also { session.detected = it }
        } else {
            session.src
        }

        // auto-detect mein src==tgt ho jaaye to target flip kar do (Gboard jaisa)
        val effectiveTgt = if (effectiveSrc == session.tgt) {
            if (session.tgt == TranslateLang.HINDI) TranslateLang.ENGLISH else TranslateLang.HINDI
        } else {
            session.tgt
        }

        // ── model ready nahi? download maango (hide-nothing: batao kya ho raha hai) ─
        if (!engine.isModelReady(effectiveSrc, effectiveTgt)) {
            session.modelReady = false
            session.phase = TranslatePhase.DOWNLOADING
            bump()
            downloadModel()
            return
        }

        val token = ++requestToken
        session.phase = TranslatePhase.TRANSLATING
        bump()
        engine.translate(effectiveSrc, effectiveTgt, text) { result ->
            // stale result discard — user ne tab tak aur type kar diya hoga
            if (token != requestToken) return@translate
            if (result == null) {
                session.phase = TranslatePhase.ERROR
                session.error = TranslateError.TRANSLATE_FAILED
                session.output = ""
            } else {
                session.output = result
                session.phase = TranslatePhase.READY
                session.error = null
            }
            bump()
        }
    }

    private fun bump() { onChanged?.invoke() }

    companion object {
        /** Gboard "translate as you type" — 300 ms debounce practical hai. */
        const val DEBOUNCE_MS = 300L

        /** Wi-Fi-only download default (research: ~30 MB per language). */
        const val DEFAULT_WIFI_ONLY_DOWNLOAD = true
    }
}
