// android-only: ML Kit dependency — JVM test runner is file ko skip karta hai
package com.mgboard.keyboard.ime

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.mgboard.keyboard.translate.TranslateEngine
import com.mgboard.keyboard.translate.TranslateLang

/**
 * [TranslateEngine] ka **ML Kit** implementation — translate panel ka backend.
 *
 * Research (`docs/research/toolbar-project/TRANSLATE-GIF-FEASIBILITY.md` §1.1):
 *  - dependency: `com.google.mlkit:translate` (standalone SDK, Firebase ki zaroorat nahi)
 *  - **59 languages**, `hi` Hindi + `en` English officially supported
 *  - model **~30 MB per language**, ek baar download → uske baad **poora offline**
 *  - **koi permission nahi** chahiye (INTERNET sirf model download ke liye, jo pehle se hai)
 *  - ML Kit **English-pivot** hai → hi↔en direct pair (best quality), baaki via English
 *  - `Translator.close()` zaroori hai warna resource leak — [close] isi liye hai
 *  - Gboard translate ki tarah internet par depend nahi karta (Gboard Google ki web
 *    translation service use karta hai), isliye MgBoard par privacy behtar hai
 *
 * Design note: har (src, tgt) pair ka `Translator` cache hota hai — ML Kit ka client
 * sasta nahi hai, aur translate panel "type karte raho" wala flow hai.
 */
class MlKitTranslateEngine(
    /** Model download sirf Wi-Fi par (default true — ~30 MB per language). */
    private val wifiOnly: Boolean = true,
) : TranslateEngine {

    private val modelManager = RemoteModelManager.getInstance()
    private val translators = HashMap<String, Translator>()

    // ── readiness ───────────────────────────────────────────────────────────

    /**
     * ML Kit mein pivot English hota hai, isliye pair ke **dono** languages ke model
     * device par hone chahiye (hi→en ke liye hi model; en→hi ke liye en model).
     */
    override fun isModelReady(src: TranslateLang, tgt: TranslateLang): Boolean {
        val codes = setOf(src.code, tgt.code)
        val downloaded = downloadedCodes()
        return codes.all { it in downloaded }
    }

    private var downloadedCache: Set<String>? = null

    private fun downloadedCodes(): Set<String> {
        downloadedCache?.let { return it }
        val out = HashSet<String>()
        val latch = java.util.concurrent.CountDownLatch(1)
        modelManager.getDownloadedModels(TranslateRemoteModel::class.java)
            .addOnSuccessListener { models ->
                models.forEach { m -> out += m.language }
                downloadedCache = out
                latch.countDown()
            }
            .addOnFailureListener { latch.countDown() }
        // IME main thread par hai; ML Kit ka task apne executor par chalta hai.
        // 1.5 s tak wait — na milne par "not ready" maan lenge (dobara poochha jaayega).
        try { latch.await(1500, java.util.concurrent.TimeUnit.MILLISECONDS) } catch (_: Exception) {}
        return downloadedCache ?: emptySet()
    }

    /** Model list refresh karo (download ke baad cache stale na rahe). */
    fun invalidateModelCache() { downloadedCache = null }

    // ── download ────────────────────────────────────────────────────────────

    override fun downloadModel(
        src: TranslateLang,
        tgt: TranslateLang,
        onProgress: (Float) -> Unit,
        onDownloaded: (Boolean) -> Unit,
    ) {
        val needed = listOf(src, tgt).distinct()
        val conditions = DownloadConditions.Builder().apply {
            if (wifiOnly) requireWifi()
        }.build()

        // ML Kit ke public API mein per-byte progress callback nahi hai, isliye
        // progress language-count ke hisaab se deterministic dikhate hain.
        var done = 0
        var allOk = true
        onProgress(0f)

        fun finish() {
            invalidateModelCache()
            onProgress(if (allOk) 1f else done.toFloat() / needed.size)
            onDownloaded(allOk)
        }

        fun downloadNext(index: Int) {
            if (index >= needed.size) { finish(); return }
            val lang = needed[index]
            val model = TranslateRemoteModel.Builder(mlkitCode(lang)).build()
            modelManager.download(model, conditions)
                .addOnSuccessListener {
                    done++
                    onProgress(done.toFloat() / needed.size)
                    downloadNext(index + 1)
                }
                .addOnFailureListener {
                    allOk = false
                    finish()
                }
        }
        downloadNext(0)
    }

    // ── translate ───────────────────────────────────────────────────────────

    override fun translate(
        src: TranslateLang,
        tgt: TranslateLang,
        text: String,
        onResult: (String?) -> Unit,
    ) {
        val translator = translatorFor(src, tgt)
        if (translator == null) { onResult(null); return }
        translator.translate(text)
            .addOnSuccessListener { onResult(it) }
            .addOnFailureListener { onResult(null) }
    }

    private fun translatorFor(src: TranslateLang, tgt: TranslateLang): Translator? {
        val key = "${src.code}>${tgt.code}"
        translators[key]?.let { return it }
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(mlkitCode(src))
            .setTargetLanguage(mlkitCode(tgt))
            .build()
        val t = Translation.getClient(options)
        translators[key] = t
        return t
    }

    /** ML Kit ke `TranslateLanguage.*` constants — BCP-47 tag se map. */
    private fun mlkitCode(lang: TranslateLang): String = when (lang) {
        TranslateLang.HINDI -> TranslateLanguage.HINDI
        TranslateLang.ENGLISH -> TranslateLanguage.ENGLISH
        TranslateLang.BENGALI -> TranslateLanguage.BENGALI
        TranslateLang.GUJARATI -> TranslateLanguage.GUJARATI
        TranslateLang.KANNADA -> TranslateLanguage.KANNADA
        TranslateLang.MARATHI -> TranslateLanguage.MARATHI
        TranslateLang.TAMIL -> TranslateLanguage.TAMIL
        TranslateLang.TELUGU -> TranslateLanguage.TELUGU
        TranslateLang.URDU -> TranslateLanguage.URDU
    }

    // ── lifecycle ───────────────────────────────────────────────────────────

    /**
     * ML Kit docs: *"Ensure the `close()` method is called when the `Translator`
     * object is no longer needed."* Panel band hone par model call karta hai.
     */
    override fun close() {
        translators.values.forEach { runCatching { it.close() } }
        translators.clear()
    }
}
