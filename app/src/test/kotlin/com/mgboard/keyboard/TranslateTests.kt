package com.mgboard.keyboard

import com.mgboard.keyboard.translate.TranslateController
import com.mgboard.keyboard.translate.TranslateEngine
import com.mgboard.keyboard.translate.TranslateError
import com.mgboard.keyboard.translate.TranslateLang
import com.mgboard.keyboard.translate.TranslatePhase
import com.mgboard.keyboard.translate.ScriptKind
import com.mgboard.keyboard.translate.detectScript
import com.mgboard.keyboard.translate.isDirectEnglishPair

/**
 * Translate panel (toolbar-project ka TRANSLATE access point) — ML Kit on-device
 * engine ke seam ke peeche ka poora state machine.
 *
 * Research: `docs/research/toolbar-project/TRANSLATE-GIF-FEASIBILITY.md`
 *  - ML Kit 59 languages support karta hai, `hi` Hindi + `en` English officially listed
 *  - ML Kit English-pivot hai → hi↔en direct pair (best quality)
 *  - ML Kit language-ID `hi` ko Devanagari maanta hai → romanized Hinglish honest-gate
 *  - model ~30 MB per language, Wi-Fi-only download recommended
 *  - Gboard UI: source picker / ⇄ swap / target picker / ✓ insert
 */
object TranslateTests {

    // ── fake engine (JVM par ML Kit ki jagah) ───────────────────────────────
    private class FakeEngine : TranslateEngine {
        val ready = mutableSetOf<Pair<String, String>>()
        /** key = "src>tgt", value = output ya null (failure). */
        val results = mutableMapOf<String, String?>()
        var translateCalls = ArrayList<Triple<String, String, String>>()
        var downloadCalls = 0
        var downloadOk = true
        var closed = 0
        /** download ko async dikhane ke liye: true = callbacks turant chalenge. */
        var autoDownload = true

        fun readyFor(vararg pairs: Pair<TranslateLang, TranslateLang>) {
            pairs.forEach { ready += it.first.code to it.second.code }
        }

        override fun isModelReady(src: TranslateLang, tgt: TranslateLang) =
            (src.code to tgt.code) in ready

        override fun downloadModel(
            src: TranslateLang,
            tgt: TranslateLang,
            onProgress: (Float) -> Unit,
            onDownloaded: (Boolean) -> Unit,
        ) {
            downloadCalls++
            if (!autoDownload) return
            if (downloadOk) {
                onProgress(0.5f); onProgress(1f)
                ready += src.code to tgt.code
                // ML Kit English-pivot: reverse pair bhi ready maano
                ready += tgt.code to src.code
                onDownloaded(true)
            } else {
                onDownloaded(false)
            }
        }

        override fun translate(
            src: TranslateLang,
            tgt: TranslateLang,
            text: String,
            onResult: (String?) -> Unit,
        ) {
            translateCalls += Triple(src.code, tgt.code, text)
            onResult(results["${src.code}>${tgt.code}"])
        }

        override fun close() { closed++ }
    }

    /** Deterministic manual timer — tests khud fire karte hain. */
    private class Timer {
        /** (id, delay, runnable) */
        val pending = ArrayList<Triple<Long, Long, () -> Unit>>()
        var cancelled = 0
        var nextId = 1L
        fun schedule(delay: Long, r: () -> Unit): Long {
            val id = nextId++
            pending += Triple(id, delay, r)
            return id
        }
        fun cancel(id: Long) { cancelled++; pending.removeAll { it.first == id } }
        fun fireAll() { val copy = pending.toList(); pending.clear(); copy.forEach { it.third() } }
        val count get() = pending.size
        val lastDelay get() = pending.last().second
    }

    private val HI = TranslateLang.HINDI
    private val EN = TranslateLang.ENGLISH

    /** Devanagari test strings explicit code points se (NFD/typing ambiguity se bachne ke liye). */
    private val dKa = "\u0915"; private val dMa = "\u092E"; private val dLa = "\u0932"
    private val dAA = "\u093E"
    private val devText = dKa + dMa + dLa            // कमल
    private val devLong = dKa + dAA + "\u092E"        // काम

    private fun ctrl(
        engine: TranslateEngine?,
        timer: Timer,
        src: TranslateLang = EN,
        tgt: TranslateLang = HI,
        autoDetect: Boolean = true,
        uiHindi: Boolean = false,
        debounce: Long = TranslateController.DEBOUNCE_MS,
    ): TranslateController {
        val c = TranslateController(
            engineProvider = { engine },
            scheduleTimer = { d, r -> timer.schedule(d, r) },
            cancelTimer = { timer.cancel(it) },
            debounceMs = debounce,
            uiHindi = uiHindi,
        )
        c.session.src = src
        c.session.tgt = tgt
        c.session.autoDetect = autoDetect
        return c
    }

    fun run() {
        languages()
        scriptDetection()
        errorsAndLabels()
        controllerFlow()
        modelDownload()
        debouncing()
    }

    // ══════════════ 1. language inventory (ML Kit official list) ══════════════
    private fun languages() {
        T.section("Translate — languages (ML Kit supported list)")

        T.eq("Hindi code = hi (ML Kit TranslateLanguage.HINDI)", HI.code, "hi")
        T.eq("Hindi native label", HI.native, "\u0939\u093F\u0928\u094D\u0926\u0940")
        T.eq("Hindi EN name", HI.enName, "Hindi")
        T.eq("English code = en", EN.code, "en")
        T.eq("English label same in both UI languages", EN.label(false), EN.label(true))
        T.eq("Hindi label switches with UI language",
            listOf(HI.label(false), HI.label(true)), listOf("Hindi", HI.native))

        T.eq("ML Kit hi+en dono supported hain",
            listOf(TranslateLang.fromCode("hi"), TranslateLang.fromCode("en")), listOf(HI, EN))
        T.ok("unknown code → null (Punjabi ML Kit translate list mein NAHI hai)",
            TranslateLang.fromCode("pa") == null)
        T.ok("null code → null", TranslateLang.fromCode(null) == null)

        T.eq("India-relevant languages included (bn/gu/kn/mr/ta/te/ur)",
            listOf("bn", "gu", "kn", "mr", "ta", "te", "ur").map { TranslateLang.fromCode(it)?.name },
            listOf("BENGALI", "GUJARATI", "KANNADA", "MARATHI", "TAMIL", "TELUGU", "URDU"))
        T.eq("total supported in model = 9", TranslateLang.values().size, 9)

        // MgBoard ka primary use-case: Hindi ↔ English
        T.eq("default pair (Hindi UI) = hi → en", TranslateLang.defaultPair(true), HI to EN)
        T.eq("default pair (English UI) = en → hi", TranslateLang.defaultPair(false), EN to HI)

        // ML Kit English pivot rule
        T.ok("hi→en direct pair", isDirectEnglishPair(HI, EN))
        T.ok("en→hi direct pair", isDirectEnglishPair(EN, HI))
        T.ok("hi→mr NOT direct (English pivot se jaayega)", !isDirectEnglishPair(HI, TranslateLang.MARATHI))
        T.ok("bn→en direct", isDirectEnglishPair(TranslateLang.BENGALI, EN))

        T.eq("model size constant ~30 MB (research)", TranslateEngine.MODEL_SIZE_MB, 30)
    }

    // ══════════════ 2. script detection (Hinglish honesty) ══════════════
    private fun scriptDetection() {
        T.section("Translate — script detection (romanized Hindi gate)")

        T.eq("Devanagari text → DEVANAGARI", detectScript(devText), ScriptKind.DEVANAGARI)
        T.eq("Devanagari + matra → DEVANAGARI", detectScript(devLong), ScriptKind.DEVANAGARI)
        T.eq("Latin text → LATIN", detectScript("kaise ho bhai"), ScriptKind.LATIN)
        T.eq("empty → EMPTY", detectScript(""), ScriptKind.EMPTY)
        T.eq("spaces/digits only → EMPTY", detectScript("  123 .,"), ScriptKind.EMPTY)
        T.eq("mixed Devanagari + Latin → MIXED", detectScript(devText + " ok"), ScriptKind.MIXED)
        // Gondi text translate panel ka case hai hi nahi (keyboard Gondi glyphs insert karta
        // hai, translate Devanagari↔English ke liye hai) — surrogates neutral maane jaate hain.
        T.eq("Gondi letter (U+11D0C) neutral → EMPTY",
            detectScript(String(intArrayOf(0x11D0C), 0, 1)), ScriptKind.EMPTY)
        T.eq("Gondi text + Latin → LATIN (surrogates ignore)",
            detectScript(String(intArrayOf(0x11D0C), 0, 1) + " ok"), ScriptKind.LATIN)
        T.eq("rupee symbol (U+20B9) → OTHER", detectScript("\u20B9"), ScriptKind.OTHER)
        T.eq("OTHER + ASCII digits → OTHER (digits neutral hain)", detectScript("\u20B9\u20B912"), ScriptKind.OTHER)
        T.eq("Devanagari danda (U+0964) block mein hai → DEVANAGARI", detectScript("\u0964"), ScriptKind.DEVANAGARI)
        T.eq("Devanagari digits ignored (U+0966 in block → counts as Devanagari)",
            detectScript("\u0968\u0966"), ScriptKind.DEVANAGARI)
    }

    // ══════════════ 3. error strings (hide-nothing, verbatim) ══════════════
    private fun errorsAndLabels() {
        T.section("Translate — gated reasons (EN + HI)")

        for (e in TranslateError.values()) {
            T.ok("${e.name} has EN reason", e.en.isNotEmpty())
            T.ok("${e.name} has HI reason", e.hi.isNotEmpty())
            T.eq("${e.name} label switches with UI language",
                listOf(e.label(false), e.label(true)), listOf(e.en, e.hi))
        }
        // Gboard ka verbatim "tool unavailable" message do jagah reuse hota hai
        T.ok("ENGINE_UNAVAILABLE = Gboard verbatim EN",
            TranslateError.ENGINE_UNAVAILABLE.en.startsWith("Can’t use this tool at the moment"))
        T.eq("TRANSLATE_FAILED reuses same Gboard message",
            TranslateError.TRANSLATE_FAILED.en, TranslateError.ENGINE_UNAVAILABLE.en)
        T.ok("UNSUPPORTED_SCRIPT mentions Romanized Hindi",
            TranslateError.UNSUPPORTED_SCRIPT.en.contains("Romanized Hindi"))
        T.ok("NO_ENGINE reason present", TranslateError.NO_ENGINE.hi.contains("\u0905\u0928\u0941\u0935\u093E\u0926"))
    }

    // ══════════════ 4. controller — main flow ══════════════
    private fun controllerFlow() {
        T.section("Translate — controller state machine")

        // ── engine hi nahi hai → NO_ENGINE (panel gated, hide-nothing) ──────
        run {
            val t = Timer(); val c = ctrl(null, t)
            var bumped = 0; c.onChanged = { bumped++ }
            c.onInput("hello")
            T.eq("phase TRANSLATING while debounced", c.session.phase, TranslatePhase.TRANSLATING)
            t.fireAll()
            T.eq("no engine → ERROR", c.session.phase, TranslatePhase.ERROR)
            T.eq("no engine → NO_ENGINE reason", c.session.error, TranslateError.NO_ENGINE)
            T.eq("output empty", c.session.output, "")
            T.ok("UI ko change signal mila", bumped > 0)
        }

        // ── auto-detect: Latin → English source ─────────────────────────────
        run {
            val t = Timer(); val e = FakeEngine()
            e.readyFor(EN to HI); e.results["en>hi"] = "\u0928\u092E\u0938\u094D\u0924\u0947"
            val c = ctrl(e, t)
            c.onInput("hello")
            t.fireAll()
            T.eq("auto-detect Latin → detected = EN", c.session.detected, EN)
            T.eq("phase READY", c.session.phase, TranslatePhase.READY)
            T.eq("output = translated", c.session.output, "\u0928\u092E\u0938\u094D\u0924\u0947")
            T.eq("engine ko en→hi call mila", e.translateCalls.last(), Triple("en", "hi", "hello"))
        }

        // ── auto-detect: Devanagari → Hindi source ──────────────────────────
        run {
            val t = Timer(); val e = FakeEngine()
            e.readyFor(HI to EN); e.results["hi>en"] = "lotus"
            val c = ctrl(e, t, src = HI, tgt = EN)
            c.onInput(devText)
            t.fireAll()
            T.eq("auto-detect Devanagari → detected = HI", c.session.detected, HI)
            T.eq("output ready", c.session.output, "lotus")
            T.eq("engine ko hi→en call mila", e.translateCalls.last(), Triple("hi", "en", devText))
        }

        // ── auto-detect flip: src==tgt ho jaaye to target flip ───────────────
        run {
            val t = Timer(); val e = FakeEngine()
            e.readyFor(HI to EN, EN to HI); e.results["hi>en"] = "lotus"
            // session src=EN tgt=EN jaisa case: auto-detect ne HI detect kiya, tgt EN hi hai → ok
            val c = ctrl(e, t, src = EN, tgt = EN)
            c.onInput(devText)
            t.fireAll()
            T.eq("detected HI", c.session.detected, HI)
            T.eq("target flip hoke EN hi raha (HI≠EN)", e.translateCalls.last().first, "hi")
            T.eq("flip ka target = EN", e.translateCalls.last().second, "en")
        }

        // ── manual source (Gboard: user khud language chun sakta hai) ───────
        run {
            val t = Timer(); val e = FakeEngine()
            e.readyFor(HI to EN); e.results["hi>en"] = "lotus"
            val c = ctrl(e, t)
            c.setAutoDetect(false)
            c.setSource(HI)
            T.ok("autoDetect off after setSource", !c.session.autoDetect)
            c.onInput(devText)
            t.fireAll()
            T.eq("manual src use hua", e.translateCalls.last(), Triple("hi", "en", devText))
            T.eq("detected null (manual mode)", c.session.detected, null)
        }

        // ── romanized Hindi + manual Hindi source → honest gate ─────────────
        run {
            val t = Timer(); val e = FakeEngine()
            e.readyFor(HI to EN); e.results["hi>en"] = "should not run"
            val c = ctrl(e, t)
            c.setAutoDetect(false)
            c.setSource(HI)
            c.onInput("kaise ho bhai")
            t.fireAll()
            T.eq("Latin text + Hindi source → ERROR", c.session.phase, TranslatePhase.ERROR)
            T.eq("reason = UNSUPPORTED_SCRIPT", c.session.error, TranslateError.UNSUPPORTED_SCRIPT)
            T.eq("engine ko koi translate call nahi gaya", e.translateCalls.size, 0)
        }

        // ── mixed script → Hindi maana jaayega (Devanagari maujood hai) ─────
        run {
            val t = Timer(); val e = FakeEngine()
            e.readyFor(HI to EN); e.results["hi>en"] = "kamal ok"
            val c = ctrl(e, t)
            c.onInput(devText + " ok")
            t.fireAll()
            T.eq("MIXED → detected HI", c.session.detected, HI)
            T.eq("translate chala", e.translateCalls.size, 1)
        }

        // ── empty input → IDLE, output clear ────────────────────────────────
        run {
            val t = Timer(); val e = FakeEngine()
            e.readyFor(EN to HI); e.results["en>hi"] = "\u0928\u092E\u0938\u094D\u0924\u0947"
            val c = ctrl(e, t)
            c.onInput("hello"); t.fireAll()
            T.eq("READY pehle", c.session.phase, TranslatePhase.READY)
            c.onInput("")
            T.eq("empty → IDLE", c.session.phase, TranslatePhase.IDLE)
            T.eq("empty → output cleared", c.session.output, "")
            T.eq("empty → error cleared", c.session.error, null)
            T.eq("empty par koi timer pending nahi", t.count, 0)
        }

        // ── translate failure → TRANSLATE_FAILED ────────────────────────────
        run {
            val t = Timer(); val e = FakeEngine()
            e.readyFor(EN to HI); e.results["en>hi"] = null      // ML Kit failure
            val c = ctrl(e, t)
            c.onInput("hello"); t.fireAll()
            T.eq("failure → ERROR", c.session.phase, TranslatePhase.ERROR)
            T.eq("failure reason", c.session.error, TranslateError.TRANSLATE_FAILED)
            T.eq("failure par output empty", c.session.output, "")
        }

        // ── ✓ insert ────────────────────────────────────────────────────────
        run {
            val t = Timer(); val e = FakeEngine()
            e.readyFor(EN to HI); e.results["en>hi"] = "\u0928\u092E\u0938\u094D\u0924\u0947"
            val c = ctrl(e, t)
            var inserted: String? = null
            c.onInsert = { inserted = it }
            T.ok("insert without output → false", !c.insertOutput())
            c.onInput("hello"); t.fireAll()
            T.ok("insert with output → true", c.insertOutput())
            T.eq("inserted text = translated output", inserted, "\u0928\u092E\u0938\u094D\u0924\u0947")
        }

        // ── ⇄ swap (Gboard: beech wala icon) ────────────────────────────────
        run {
            val t = Timer(); val e = FakeEngine()
            e.readyFor(EN to HI, HI to EN)
            e.results["en>hi"] = "\u0928\u092E\u0938\u094D\u0924\u0947"; e.results["hi>en"] = "hello"
            val c = ctrl(e, t, src = EN, tgt = HI)
            c.setAutoDetect(false)
            c.onInput("hello"); t.fireAll()
            T.eq("swap se pehle src/tgt", c.session.src to c.session.tgt, EN to HI)
            c.swap()
            T.eq("swap → src/tgt ulta", c.session.src to c.session.tgt, HI to EN)
            T.eq("swap → purana output naya input (Gboard behaviour)",
                c.session.input, "\u0928\u092E\u0938\u094D\u0924\u0947")
            T.eq("swap → output cleared", c.session.output, "")
            T.eq("swap → detected cleared", c.session.detected, null)
            t.fireAll()
            T.eq("swap ke baad hi→en chala", e.translateCalls.last(), Triple("hi", "en", "\u0928\u092E\u0938\u094D\u0924\u0947"))
            T.eq("swap ke baad output", c.session.output, "hello")
        }

        // ── setTarget model status refresh karta hai ────────────────────────
        run {
            val t = Timer(); val e = FakeEngine(); e.readyFor(EN to HI)
            val c = ctrl(e, t, src = EN, tgt = HI)
            c.refreshModelStatus()
            T.ok("ready pair → modelReady true", c.session.modelReady)
            c.setTarget(TranslateLang.MARATHI)
            T.ok("naya pair ready nahi → modelReady false", !c.session.modelReady)
            T.eq("target changed", c.session.tgt, TranslateLang.MARATHI)
        }

        // ── release(): ML Kit Translator.close() + stale results ────────────
        run {
            val t = Timer(); val e = FakeEngine()
            e.readyFor(EN to HI); e.results["en>hi"] = "namaste"
            val c = ctrl(e, t)
            c.onInput("hello")
            T.eq("pending timer release se pehle", t.count, 1)
            c.release()
            T.eq("engine.close() call hua (ML Kit leak guard)", e.closed, 1)
            T.eq("pending timer cancel hua", t.count, 0)
            t.fireAll()
            T.eq("release ke baad translate nahi chala", e.translateCalls.size, 0)
        }

        // ── translateNow(): debounce skip ───────────────────────────────────
        run {
            val t = Timer(); val e = FakeEngine()
            e.readyFor(EN to HI); e.results["en>hi"] = "namaste"
            val c = ctrl(e, t)
            c.onInput("hello")
            T.eq("abhi tak translate nahi hua (debounce)", e.translateCalls.size, 0)
            c.translateNow()
            T.eq("translateNow → turant translate", e.translateCalls.size, 1)
            T.eq("pending timer cancel", t.count, 0)
            T.eq("output ready", c.session.phase, TranslatePhase.READY)
        }
    }

    // ══════════════ 5. model download (ML Kit ~30 MB, Wi-Fi) ══════════════
    private fun modelDownload() {
        T.section("Translate — ML Kit model download")

        // model ready nahi → download flow
        run {
            val t = Timer(); val e = FakeEngine()
            e.results["en>hi"] = "namaste"
            val c = ctrl(e, t)
            T.ok("shuru mein modelReady false", !c.session.modelReady)
            c.onInput("hello")
            t.fireAll()
            T.eq("download call hua", e.downloadCalls, 1)
            T.eq("download ke baad modelReady true", c.session.modelReady, true)
            T.eq("download progress 1.0", c.session.downloadProgress, 1f)
            T.eq("download ke baad translate bhi chal gaya", c.session.phase, TranslatePhase.READY)
            T.eq("output", c.session.output, "namaste")
        }

        // download fail
        run {
            val t = Timer(); val e = FakeEngine()
            e.downloadOk = false
            val c = ctrl(e, t)
            c.downloadModel()
            T.eq("download fail → ERROR", c.session.phase, TranslatePhase.ERROR)
            T.eq("download fail reason", c.session.error, TranslateError.DOWNLOAD_FAILED)
            T.ok("modelReady abhi bhi false", !c.session.modelReady)
            T.eq("progress reset", c.session.downloadProgress, 0f)
        }

        // explicit downloadModel when already ready
        run {
            val t = Timer(); val e = FakeEngine(); e.readyFor(EN to HI)
            val c = ctrl(e, t)
            c.downloadModel()
            T.eq("ready model par download call nahi", e.downloadCalls, 0)
            T.ok("modelReady true", c.session.modelReady)
            T.eq("phase IDLE (koi input nahi)", c.session.phase, TranslatePhase.IDLE)
        }

        // async download: progress dikhta hai, phir ready
        run {
            val t = Timer(); val e = FakeEngine(); e.autoDownload = false
            val c = ctrl(e, t)
            c.downloadModel()
            T.eq("async download → phase DOWNLOADING", c.session.phase, TranslatePhase.DOWNLOADING)
            T.eq("progress abhi 0", c.session.downloadProgress, 0f)
            T.ok("modelReady false", !c.session.modelReady)
        }

        // refreshModelStatus: NO_ENGINE detection
        run {
            val t = Timer(); val c = ctrl(null, t)
            c.refreshModelStatus()
            T.ok("engine nahi → modelReady false", !c.session.modelReady)
            T.eq("engine nahi + input nahi → IDLE", c.session.phase, TranslatePhase.IDLE)
            c.onInput("hi there")            // timer pending, engine null
            c.refreshModelStatus()
            T.eq("engine nahi + input hai → ERROR", c.session.phase, TranslatePhase.ERROR)
            T.eq("reason NO_ENGINE", c.session.error, TranslateError.NO_ENGINE)
        }

        T.eq("default Wi-Fi-only download policy = true",
            TranslateController.DEFAULT_WIFI_ONLY_DOWNLOAD, true)
    }

    // ══════════════ 6. debouncing + stale results ══════════════
    private fun debouncing() {
        T.section("Translate — debounce (Gboard 'translate as you type')")

        T.eq("debounce default = 300 ms", TranslateController.DEBOUNCE_MS, 300L)

        // har keystroke par timer reset
        run {
            val t = Timer(); val e = FakeEngine()
            e.readyFor(EN to HI); e.results["en>hi"] = "namaste"
            val c = ctrl(e, t)
            c.onInput("h"); c.onInput("he"); c.onInput("hel"); c.onInput("hello")
            T.eq("sirf ek timer pending (baaki cancel)", t.count, 1)
            T.ok("purane timers cancel hue", t.cancelled >= 3)
            t.fireAll()
            T.eq("sirf aakhri text translate hua", e.translateCalls, arrayListOf(Triple("en", "hi", "hello")))
        }

        // stale result discard — engine async hai, user ne tab tak aur type kiya
        run {
            val t = Timer()
            val e = object : TranslateEngine {
                val cbs = ArrayList<(String?) -> Unit>()
                override fun isModelReady(src: TranslateLang, tgt: TranslateLang) = true
                override fun downloadModel(src: TranslateLang, tgt: TranslateLang,
                                           onProgress: (Float) -> Unit, onDownloaded: (Boolean) -> Unit) {}
                override fun translate(src: TranslateLang, tgt: TranslateLang, text: String,
                                       onResult: (String?) -> Unit) { cbs += onResult }
                override fun close() {}
            }
            val c = ctrl(e, t)
            c.onInput("hello"); t.fireAll()            // request #1
            T.eq("pehla request TRANSLATING", c.session.phase, TranslatePhase.TRANSLATING)
            c.onInput("hello world"); t.fireAll()      // request #2 → token aage badh gaya
            T.eq("do requests engine tak pahunche", e.cbs.size, 2)
            e.cbs[0]("purana natija")                  // request #1 ka result AB aaya (stale)
            T.eq("stale result discard — phase abhi bhi TRANSLATING",
                c.session.phase, TranslatePhase.TRANSLATING)
            T.eq("stale result se output set nahi hua", c.session.output, "")
            e.cbs[1]("namaste duniya")                 // request #2 ka result
            T.eq("current request ka result accept", c.session.phase, TranslatePhase.READY)
            T.eq("output = current request ka", c.session.output, "namaste duniya")
        }

        // custom debounce injectable
        run {
            val t = Timer(); val e = FakeEngine()
            e.readyFor(EN to HI); e.results["en>hi"] = "x"
            val c = ctrl(e, t, debounce = 999L)
            c.onInput("hello")
            T.eq("custom debounce use hua", t.lastDelay, 999L)
        }
    }
}
