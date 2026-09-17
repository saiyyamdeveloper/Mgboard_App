package com.mgboard.keyboard.media

/**
 * GIF / Stickers layer ka model — **pure Kotlin** (JVM-testable).
 *
 * Research: `docs/research/toolbar-project/TRANSLATE-GIF-FEASIBILITY.md` §2
 *
 *  - **Tenor API 30 June 2026 ko band ho gaya** (Gboard ka GIF source) — naye
 *    keyboards ke liye available nahi; isliye provider abstraction zaroori hai
 *  - **Klipy** (ex-Tenor team, free-for-life) = search provider; API key chahiye,
 *    attribution zaroori, testing mode mein 100 req/hour
 *  - **Bundled sticker pack** = zero-dependency tier (offline, koi key nahi)
 *  - Insertion **Commit Content API** se hota hai (Android side, `MediaCommitController`):
 *    editor ko `contentMimeTypes` se opt-in karna padta hai; support na ho to Gboard ka
 *    verbatim message — *"The text field does not support GIF insertion from the keyboard"*
 */

// ══════════════════════════ bundled stickers ══════════════════════════

/** Pack ka ek sticker — `res/raw/stickers_manifest.json` se generated (scripts/gen_stickers.py). */
data class BundledSticker(
    val id: String,
    /** Android resource path, e.g. `raw/sticker_hi` (FileProvider se serve hota hai). */
    val res: String,
    val title: String,
    /** Hindi label. */
    val hi: String,
    val tags: List<String>,
) {
    val mime: String get() = "image/png"
}

// ══════════════════════════ provider API ══════════════════════════

/** Search/trending ka ek media item (GIF ya sticker). */
data class MediaItem(
    val slug: String,
    val title: String,
    /** Chhota preview (grid ke liye) — Klipy `sm.webp`/`sm.gif`. */
    val previewUrl: String,
    /** Jo editor ko bhejna hai — Klipy `md.gif` (image/gif) ya `md.webp`. */
    val fullUrl: String,
    val mime: String,
    val width: Int = 0,
    val height: Int = 0,
    val provider: String = "klipy",
)

data class MediaPage(val items: List<MediaItem>, val page: Int, val hasNext: Boolean)

/**
 * Tab available hai ya kyun nahi — hide-nothing: tab chhupta nahi, wajah batata hai.
 * Android side isi se decide karti hai grid dikhe ya reason.
 */
sealed class MediaAvailability {
    object Available : MediaAvailability()
    /** Editor ne is MIME ko accept nahi kiya (Commit Content API opt-in chahiye). */
    data class EditorUnsupported(val mime: String) : MediaAvailability()
    /** API key configure nahi hai — setup hint dikhega. */
    object NoApiKey : MediaAvailability()
    /** Internet nahi hai / request fail. */
    object Offline : MediaAvailability()

    val isAvailable: Boolean get() = this == Available

    /** Gboard-verbatim / setup reasons (EN + HI). */
    fun reason(hindi: Boolean): String? = when (this) {
        Available -> null
        is EditorUnsupported -> if (hindi)
            "यह टेक्स्ट फ़ील्ड कीबोर्ड से GIF डालने का समर्थन नहीं करती"
        else
            "The text field does not support GIF insertion from the keyboard"
        NoApiKey -> if (hindi)
            "GIF खोज के लिए Klipy API key जोड़ें (Settings → Klipy API key). Bundled stickers नीचे चलते रहेंगे."
        else
            "Add a Klipy API key for GIF search (Settings → Klipy API key). Bundled stickers keep working below."
        Offline -> if (hindi)
            "अभी इस टूल का उपयोग नहीं किया जा सकता. कृपया बाद में फिर कोशिश करें."
        else
            "Can't use this tool at the moment. Please try again later."
    }
}

/** Provider seam — bundled pack, Klipy, future mein Giphy sab isi interface se. */
interface MediaProvider {
    val id: String
    fun availability(editorSupportsMime: Boolean): MediaAvailability
    fun trending(page: Int = 1, perPage: Int = 24, onResult: (MediaPage?) -> Unit)
    fun search(query: String, page: Int = 1, perPage: Int = 24, onResult: (MediaPage?) -> Unit)
    /** Provider ka attribution (Klipy/Giphy ke terms mein zaroori). */
    fun attribution(): String?
}

// ══════════════════════════ Klipy ══════════════════════════

/**
 * Klipy API ka pure-Kotlin client — request URLs banana aur response parse karna.
 * Transport (`httpGet`) inject hota hai, isliye yeh JVM par test hota hai; Android
 * side network call background thread par karti hai.
 *
 * Docs (docs.klipy.com):
 *  - base: `https://api.klipy.com`
 *  - endpoints: `/api/v1/{app_key}/gifs/trending|search`, `.../stickers/trending|search`
 *  - params: `page`, `per_page` (**8..50**, default 24), `q`, `customer_id`, `locale`,
 *    `format_filter`, `content_filter`
 *  - response: `{result, data:{data:[{id,slug,title,file:{hd,md,sm,xs}.{gif,webp,jpg,mp4,webm}
 *    .{url,width,height,size}}, tags, type}], current_page, per_page, has_next}}`
 *  - testing-mode key = **100 requests/hour** → search debounce/explicit-submit zaroori
 *  - attribution UI mein zaroori; results ka order change karna mana hai
 */
class KlipyApi(
    private val appKey: String,
    private val httpGet: (String) -> String?,
) {
    val enabled: Boolean get() = appKey.isNotBlank()

    fun trendingUrl(kind: Kind, page: Int, perPage: Int, locale: String, contentFilter: String): String =
        baseUrl(kind, "trending", null, page, perPage, locale, contentFilter)

    fun searchUrl(kind: Kind, q: String, page: Int, perPage: Int, locale: String, contentFilter: String): String =
        baseUrl(kind, "search", q, page, perPage, locale, contentFilter)

    private fun baseUrl(
        kind: Kind, op: String, q: String?, page: Int, perPage: Int,
        locale: String, contentFilter: String,
    ): String {
        val pp = perPage.coerceIn(MIN_PER_PAGE, MAX_PER_PAGE)
        val sb = StringBuilder(BASE)
            .append("/api/v1/").append(appKey).append('/').append(kind.path).append('/').append(op)
            .append("?page=").append(page.coerceAtLeast(1))
            .append("&per_page=").append(pp)
        if (q != null) sb.append("&q=").append(urlEncode(q))
        if (locale.isNotBlank()) sb.append("&locale=").append(urlEncode(locale))
        if (contentFilter.isNotBlank()) sb.append("&content_filter=").append(urlEncode(contentFilter))
        return sb.toString()
    }

    /** Network + parse. Fail/null → `null` (caller Offline availability dikhayega). */
    fun fetch(url: String): MediaPage? {
        val body = httpGet(url) ?: return null
        return parsePage(body)
    }

    enum class Kind(val path: String) { GIFS("gifs"), STICKERS("stickers") }

    companion object {
        const val BASE = "https://api.klipy.com"
        /** docs: per_page minimum 8, maximum 50, default 24. */
        const val MIN_PER_PAGE = 8
        const val MAX_PER_PAGE = 50
        const val DEFAULT_PER_PAGE = 24
        /** testing-mode key ka rate limit — isi liye explicit submit / debounce. */
        const val TESTING_RATE_LIMIT_PER_HOUR = 100

        /**
         * Response parse — [MiniJson] se. `sm.webp` preview (chhota, animated),
         * `md.gif` full (editor ko yahi jaata hai).
         */
        fun parsePage(json: String): MediaPage? {
            val root = MiniJson.parse(json) as? Map<*, *> ?: return null
            if (root["result"] != true) return null
            val data = root["data"] as? Map<*, *> ?: return null
            val arr = data["data"] as? List<*> ?: return null
            val items = ArrayList<MediaItem>(arr.size)
            for (raw in arr) {
                val obj = raw as? Map<*, *> ?: continue
                val slug = obj["slug"] as? String ?: continue
                val title = obj["title"] as? String ?: ""
                val file = obj["file"] as? Map<*, *> ?: continue
                val sm = file["sm"] as? Map<*, *>
                val md = file["md"] as? Map<*, *>
                val preview = (sm?.get("webp") as? Map<*, *>) ?: (sm?.get("gif") as? Map<*, *>)
                val full = (md?.get("gif") as? Map<*, *>) ?: (md?.get("webp") as? Map<*, *>)
                val previewUrl = preview?.get("url") as? String ?: continue
                val fullUrl = full?.get("url") as? String ?: previewUrl
                val mime = when {
                    full === md?.get("gif") -> "image/gif"
                    else -> "image/webp"
                }
                items += MediaItem(
                    slug = slug,
                    title = title,
                    previewUrl = previewUrl,
                    fullUrl = fullUrl,
                    mime = mime,
                    width = (full?.get("width") as? Number)?.toInt() ?: 0,
                    height = (full?.get("height") as? Number)?.toInt() ?: 0,
                )
            }
            return MediaPage(
                items = items,
                page = (data["current_page"] as? Number)?.toInt() ?: 1,
                hasNext = data["has_next"] == true,
            )
        }

        /** Minimal URL-encode (query string ke liye kaafi). */
        fun urlEncode(s: String): String = buildString {
            for (ch in s) when {
                // sirf ASCII unreserved chars raw — Devanagari/Latin-Extended percent-encode
                (ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9') || ch in "-_.~" -> append(ch)
                ch == ' ' -> append('+')
                else -> ch.toString().toByteArray(Charsets.UTF_8).forEach {
                    append('%'); append(HEX[(it.toInt() shr 4) and 0xF]); append(HEX[it.toInt() and 0xF])
                }
            }
        }

        private val HEX = "0123456789ABCDEF".toCharArray()
    }
}

// ══════════════════════════ MiniJson (dependency-free) ══════════════════════════

/**
 * Chhota recursive-descent JSON parser — koi dependency nahi, JVM par test hota hai.
 * Objects → `Map<String, Any?>`, arrays → `List<Any?>`, strings/numbers/booleans/null.
 */
object MiniJson {
    fun parse(text: String): Any? = Parser(text).parseValue().also { /* trailing ignore */ }

    private class Parser(private val s: String) {
        private var i = 0

        fun parseValue(): Any? {
            skipWs()
            if (i >= s.length) return null
            return when (val c = s[i]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't' -> expect("true", true)
                'f' -> expect("false", false)
                'n' -> expect("null", null)
                else -> if (c == '-' || c.isDigit()) parseNumber() else null
            }
        }

        private fun expect(lit: String, value: Any?): Any? {
            if (s.regionMatches(i, lit, 0, lit.length)) i += lit.length
            return value
        }

        private fun parseObject(): Map<String, Any?> {
            val out = LinkedHashMap<String, Any?>()
            i++ // {
            skipWs()
            if (i < s.length && s[i] == '}') { i++; return out }
            while (i < s.length) {
                skipWs()
                val key = parseString()
                skipWs()
                if (i < s.length && s[i] == ':') i++
                out[key] = parseValue()
                skipWs()
                if (i < s.length && s[i] == ',') { i++; continue }
                if (i < s.length && s[i] == '}') { i++; break }
                break
            }
            return out
        }

        private fun parseArray(): List<Any?> {
            val out = ArrayList<Any?>()
            i++ // [
            skipWs()
            if (i < s.length && s[i] == ']') { i++; return out }
            while (i < s.length) {
                out += parseValue()
                skipWs()
                if (i < s.length && s[i] == ',') { i++; continue }
                if (i < s.length && s[i] == ']') { i++; break }
                break
            }
            return out
        }

        private fun parseString(): String {
            val sb = StringBuilder()
            i++ // "
            while (i < s.length) {
                val c = s[i]
                if (c == '"') { i++; break }
                if (c == '\\' && i + 1 < s.length) {
                    i++
                    when (val e = s[i]) {
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            if (i + 4 < s.length) {
                                val hex = s.substring(i + 1, i + 5)
                                hex.toIntOrNull(16)?.let { sb.append(it.toChar()) }
                                i += 4
                            }
                        }
                        else -> sb.append(e)
                    }
                    i++
                } else {
                    sb.append(c)
                    i++
                }
            }
            return sb.toString()
        }

        private fun parseNumber(): Number {
            val start = i
            if (i < s.length && s[i] == '-') i++
            while (i < s.length && s[i].isDigit()) i++
            var isDouble = false
            if (i < s.length && s[i] == '.') { isDouble = true; i++; while (i < s.length && s[i].isDigit()) i++ }
            if (i < s.length && (s[i] == 'e' || s[i] == 'E')) {
                isDouble = true; i++
                if (i < s.length && (s[i] == '+' || s[i] == '-')) i++
                while (i < s.length && s[i].isDigit()) i++
            }
            val raw = s.substring(start, i)
            return if (isDouble) raw.toDouble() else raw.toLongOrNull() ?: raw.toDouble()
        }

        private fun skipWs() { while (i < s.length && s[i].isWhitespace()) i++ }
    }
}
