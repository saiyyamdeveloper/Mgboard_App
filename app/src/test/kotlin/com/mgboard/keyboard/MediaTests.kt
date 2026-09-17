package com.mgboard.keyboard

import com.mgboard.keyboard.media.BundledStickers
import com.mgboard.keyboard.media.KlipyApi
import com.mgboard.keyboard.media.MediaAvailability
import com.mgboard.keyboard.media.MediaItem
import com.mgboard.keyboard.media.MediaPage
import com.mgboard.keyboard.media.MiniJson

/**
 * GIF / Stickers layer — bundled sticker pack + Klipy API client + JSON parser.
 *
 * Research: `docs/research/toolbar-project/TRANSLATE-GIF-FEASIBILITY.md` §2
 *  - Tenor API 30 Jun 2026 ko shut down (Gboard ka source third parties ke liye khatam)
 *  - Klipy: free-for-life, testing key 100 req/hr, per_page 8..50, attribution zaroori
 *  - Bundled pack: offline tier, koi key/attribution/rate-limit nahi
 *  - Insertion: Commit Content API — editor opt-in chahiye, warna Gboard verbatim toast
 */
object MediaTests {

    /** Klipy docs ke response shape ka realistic sample (docs.klipy.com/gifs-api). */
    private val SAMPLE = """
    {
      "result": true,
      "data": {
        "data": [
          {
            "id": 8041071659142944,
            "slug": "hello-hi-662",
            "title": "Hello",
            "file": {
              "md": {
                "gif":  { "url": "https://static.klipy.com/a/md.gif",  "width": 498, "height": 498, "size": 3721260 },
                "webp": { "url": "https://static.klipy.com/a/md.webp", "width": 498, "height": 498, "size": 643490 }
              },
              "sm": {
                "gif":  { "url": "https://static.klipy.com/a/sm.gif",  "width": 220, "height": 220, "size": 314884 },
                "webp": { "url": "https://static.klipy.com/a/sm.webp", "width": 220, "height": 220, "size": 80118 }
              }
            },
            "tags": ["hello", "hi"],
            "type": "gif"
          },
          {
            "id": 2,
            "slug": "namaste-wave",
            "title": "Namaste \u00B7 \u0928\u092E\u0938\u094D\u0924\u0947",
            "file": {
              "sm": { "gif": { "url": "https://static.klipy.com/b/sm.gif", "width": 90, "height": 90 } },
              "md": { "webp": { "url": "https://static.klipy.com/b/md.webp", "width": 300, "height": 300 } }
            },
            "tags": [],
            "type": "gif"
          }
        ],
        "current_page": 1,
        "per_page": 24,
        "has_next": true
      }
    }
    """.trimIndent()

    fun run() {
        bundledPack()
        miniJson()
        klipyUrls()
        klipyParsing()
        availability()
    }

    // ══════════════ 1. bundled sticker pack (offline tier) ══════════════
    private fun bundledPack() {
        T.section("Media — bundled sticker pack (zero dependency)")

        T.eq("pack id", BundledStickers.PACK_ID, "mgboard_reactions")
        T.eq("pack title", BundledStickers.PACK_TITLE, "MgBoard Reactions")
        T.ok("pack title HI", BundledStickers.PACK_TITLE_HI.isNotEmpty())
        T.eq("sticker count = 12", BundledStickers.ITEMS.size, 12)
        T.eq("sticker size 256px", BundledStickers.SIZE, 256)
        T.ok("attribution maujood (font license)", BundledStickers.ATTRIBUTION.contains("OFL"))

        T.ok("har sticker ka id unique", BundledStickers.ITEMS.map { it.id }.distinct().size == 12)
        T.ok("har sticker ka res path raw/ se", BundledStickers.ITEMS.all { it.res.startsWith("raw/sticker_") })
        T.ok("har sticker ke EN + HI labels", BundledStickers.ITEMS.all { it.title.isNotBlank() && it.hi.isNotBlank() })
        T.ok("har sticker ke 4+ tags (search ke liye)", BundledStickers.ITEMS.all { it.tags.size >= 4 })
        T.ok("sab stickers PNG (commitContent MIME)", BundledStickers.ITEMS.all { it.mime == "image/png" })

        // Hindi + English dono tags se search
        T.eq("search 'namaste' → Hi sticker", BundledStickers.search("namaste").first().id, "sticker_hi")
        T.eq("search '\u0928\u092E\u0938\u094D\u0924\u0947' (Devanagari tag) → Hi sticker",
            BundledStickers.search("\u0928\u092E\u0938\u094D\u0924\u0947").first().id, "sticker_hi")
        T.eq("search 'thank' → Thanks sticker", BundledStickers.search("thank").first().id, "sticker_thanks")
        T.eq("search '\u0927\u0928\u094D\u092F\u0935\u093E\u0926' → Thanks sticker",
            BundledStickers.search("\u0927\u0928\u094D\u092F\u0935\u093E\u0926").first().id, "sticker_thanks")
        T.eq("search case-insensitive", BundledStickers.search("WOW").first().id, "sticker_wow")
        T.eq("empty search → poora pack", BundledStickers.search("").size, 12)
        T.eq("space-only search → poora pack", BundledStickers.search("   ").size, 12)
        T.eq("unknown search → empty", BundledStickers.search("zzz-nothing").size, 0)
        T.eq("byId", BundledStickers.byId("sticker_love")?.title, "Love")
        T.eq("byId unknown → null", BundledStickers.byId("nope"), null)

        // pack coverage — common reactions
        val titles = BundledStickers.ITEMS.map { it.title }
        T.ok("reactions cover hi/thanks/wow/ok/yes/no/love/haha/hmm/sorry/morning/night",
            listOf("Hi", "Thanks", "Wow", "OK", "Yes", "No", "Love", "Haha", "Hmm", "Sorry",
                "Good morning", "Good night").all { it in titles })
    }

    // ══════════════ 2. MiniJson parser ══════════════
    private fun miniJson() {
        T.section("Media — MiniJson (dependency-free JSON)")

        @Suppress("UNCHECKED_CAST")
        val m = MiniJson.parse("""{"a":1,"b":"x","c":[1,2,{"d":true}],"e":null,"f":-2.5}""") as Map<String, Any?>
        T.eq("number", m["a"], 1L)
        T.eq("string", m["b"], "x")
        T.eq("nested array/object", (m["c"] as List<*>)[2].let { (it as Map<*, *>)["d"] }, true)
        T.eq("null", m["e"], null)
        T.eq("negative double", m["f"], -2.5)

        T.eq("string escapes \\n \\t \\u", MiniJson.parse("\"a\\nb\\tc\\u0915d\""), "a\nb\tc\u0915d")
        T.eq("escaped quote", MiniJson.parse("\"say \\\"hi\\\"\""), "say \"hi\"")
        T.eq("escaped backslash", MiniJson.parse("\"a\\\\b\""), "a\\b")
        T.eq("empty object", MiniJson.parse("{}"), emptyMap<String, Any?>())
        T.eq("empty array", MiniJson.parse("[]"), emptyList<Any?>())
        T.eq("whitespace tolerant", MiniJson.parse(" { \"x\" : [ ] } "), mapOf("x" to emptyList<Any?>()))
        T.eq("exponent number", MiniJson.parse("1.5e2"), 150.0)
        T.eq("invalid → null-safe", MiniJson.parse(""), null)
    }

    // ══════════════ 3. Klipy URL construction ══════════════
    private fun klipyUrls() {
        T.section("Media — Klipy API (URLs + rules)")

        val api = KlipyApi("TESTKEY") { null }
        T.ok("key set → enabled", api.enabled)
        T.ok("blank key → disabled", !KlipyApi(" ") { null }.enabled)

        val u = api.trendingUrl(KlipyApi.Kind.GIFS, 1, 24, "IN", "medium")
        T.ok("base url", u.startsWith(KlipyApi.BASE))
        T.ok("path = /api/v1/{key}/gifs/trending", u.contains("/api/v1/TESTKEY/gifs/trending?"))
        T.ok("page param", u.contains("page=1"))
        T.ok("per_page param", u.contains("per_page=24"))
        T.ok("locale param", u.contains("locale=IN"))
        T.ok("content filter param", u.contains("content_filter=medium"))

        val s = api.searchUrl(KlipyApi.Kind.STICKERS, "namaste wave", 3, 50, "", "")
        T.ok("stickers search path", s.contains("/api/v1/TESTKEY/stickers/search?"))
        T.ok("query URL-encoded (space → +)", s.contains("q=namaste+wave"))
        T.ok("page 3", s.contains("page=3"))
        T.ok("per_page 50 (max)", s.contains("per_page=50"))

        // docs: per_page 8..50 clamp
        T.ok("per_page 1 clamps to 8",
            api.searchUrl(KlipyApi.Kind.GIFS, "x", 1, 1, "", "").contains("per_page=8"))
        T.ok("per_page 999 clamps to 50",
            api.searchUrl(KlipyApi.Kind.GIFS, "x", 1, 999, "", "").contains("per_page=50"))
        T.ok("page 0 clamps to 1",
            api.searchUrl(KlipyApi.Kind.GIFS, "x", 0, 24, "", "").contains("page=1"))

        // Devanagari query bhi encode hota hai
        val dev = api.searchUrl(KlipyApi.Kind.GIFS, "\u0928\u092E\u0938\u094D\u0924\u0947", 1, 24, "", "")
        T.ok("Devanagari query percent-encoded", dev.contains("q=%"))
        T.ok("raw Devanagari URL mein nahi", !dev.contains("\u0928\u092E\u0938\u094D\u0924\u0947"))

        T.eq("docs constants: per_page min", KlipyApi.MIN_PER_PAGE, 8)
        T.eq("docs constants: per_page max", KlipyApi.MAX_PER_PAGE, 50)
        T.eq("docs constants: default", KlipyApi.DEFAULT_PER_PAGE, 24)
        T.eq("testing-mode rate limit = 100/hr", KlipyApi.TESTING_RATE_LIMIT_PER_HOUR, 100)

        // ── saare Klipy content types (user order: GIFs/Clips/Stickers/Memes) ──
        T.eq("4 content kinds", KlipyApi.Kind.values().map { it.path },
            listOf("gifs", "clips", "stickers", "memes"))
        T.ok("clips trending URL", api.trendingUrl(KlipyApi.Kind.CLIPS, 1, 24, "IN", "")
            .contains("/api/v1/TESTKEY/clips/trending?"))
        run {
            val mu = api.searchUrl(KlipyApi.Kind.MEMES, "funny", 2, 24, "", "")
            T.ok("memes search path", mu.contains("/api/v1/TESTKEY/memes/search?"))
            T.ok("memes query param", mu.contains("q=funny"))
            T.ok("memes page 2", mu.contains("page=2"))
        }
        T.ok("stickers trending URL", api.trendingUrl(KlipyApi.Kind.STICKERS, 1, 24, "", "")
            .contains("/api/v1/TESTKEY/stickers/trending?"))
        T.eq("kind MIMEs", KlipyApi.Kind.values().map { it.fullMime },
            listOf("image/gif", "video/mp4", "image/webp", "image/jpeg"))
        T.eq("kind exts", KlipyApi.Kind.values().map { it.ext },
            listOf("gif", "mp4", "webp", "jpg"))
        T.eq("attributions (terms mandatory)", KlipyApi.Kind.values().map { it.attribution() },
            listOf("GIFs · KLIPY", "Clips · KLIPY", "Stickers · KLIPY", "Memes · KLIPY"))
        T.eq("mimeForFormat map",
            listOf("gif", "webp", "png", "jpg", "mp4", "webm", "xyz").map { KlipyApi.mimeForFormat(it) },
            listOf("image/gif", "image/webp", "image/png", "image/jpeg", "video/mp4", "video/webm", null))
    }

    // ══════════════ 4. Klipy response parsing ══════════════
    private fun klipyParsing() {
        T.section("Media — Klipy response parse")

        val page: MediaPage = KlipyApi.parsePage(SAMPLE)!!
        T.eq("2 items", page.items.size, 2)
        T.eq("page = 1", page.page, 1)
        T.ok("hasNext", page.hasNext)

        val a = page.items[0]
        T.eq("slug", a.slug, "hello-hi-662")
        T.eq("title", a.title, "Hello")
        T.eq("preview = sm.webp", a.previewUrl, "https://static.klipy.com/a/sm.webp")
        T.eq("full = md.gif", a.fullUrl, "https://static.klipy.com/a/md.gif")
        T.eq("mime = image/gif (md.gif present)", a.mime, "image/gif")
        T.eq("dims", a.width to a.height, 498 to 498)

        // doosra item: md mein sirf webp hai → fallback
        val b = page.items[1]
        T.eq("fallback: full = md.webp", b.fullUrl, "https://static.klipy.com/b/md.webp")
        T.eq("fallback: mime = image/webp", b.mime, "image/webp")
        T.eq("Devanagari title parse hua", b.title.contains("\u0928\u092E\u0938\u094D\u0924\u0947"), true)

        // ── clips parse: md.mp4 chunta hai (video) ──────────────────────────
        run {
            val clipJson = """
            {"result":true,"data":{"data":[{
              "slug":"clip-1","title":"Celebration",
              "file":{
                "sm":{"gif":{"url":"https://s.k/1/sm.gif"}},
                "md":{"mp4":{"url":"https://s.k/1/md.mp4","width":320,"height":320},"webm":{"url":"https://s.k/1/md.webm"}}
              },"tags":[],"type":"clip"}],"current_page":2,"has_next":false}}
            """.trimIndent()
            val cp = KlipyApi.parsePage(clipJson, KlipyApi.Kind.CLIPS)!!
            T.eq("clip full = md.mp4", cp.items[0].fullUrl, "https://s.k/1/md.mp4")
            T.eq("clip mime = video/mp4", cp.items[0].mime, "video/mp4")
            T.eq("clip preview = sm.gif", cp.items[0].previewUrl, "https://s.k/1/sm.gif")
            T.eq("clip page = 2", cp.page, 2)
            T.ok("clip hasNext false", !cp.hasNext)
        }
        // ── memes parse: md.jpg chunta hai ──────────────────────────────────
        run {
            val memeJson = """
            {"result":true,"data":{"data":[{
              "slug":"meme-1","title":"Gondi dev",
              "file":{
                "sm":{"webp":{"url":"https://s.k/m/sm.webp"}},
                "md":{"jpg":{"url":"https://s.k/m/md.jpg","width":500,"height":500}}
              },"tags":[],"type":"meme"}],"current_page":1,"has_next":true}}
            """.trimIndent()
            val mp = KlipyApi.parsePage(memeJson, KlipyApi.Kind.MEMES)!!
            T.eq("meme full = md.jpg", mp.items[0].fullUrl, "https://s.k/m/md.jpg")
            T.eq("meme mime = image/jpeg", mp.items[0].mime, "image/jpeg")
        }

        // failure shapes
        T.eq("result=false → null", KlipyApi.parsePage("""{"result":false}"""), null)
        T.eq("garbage → null", KlipyApi.parsePage("not json"), null)
        T.eq("empty data array", KlipyApi.parsePage("""{"result":true,"data":{"data":[]}}""")?.items, emptyList<MediaItem>())

        // fetch() transport fail → null (caller Offline dikhayega)
        T.eq("fetch null body → null", KlipyApi("K") { null }.fetch("https://x"), null)
        var seen: String? = null
        val ok = KlipyApi("K") { url -> seen = url; SAMPLE }.fetch("https://api.klipy.com/x")
        T.eq("fetch success → page", ok?.items?.size, 2)
        T.eq("httpGet ko url mila", seen, "https://api.klipy.com/x")
    }

    // ══════════════ 5. availability + gating reasons (hide-nothing) ══════════════
    private fun availability() {
        T.section("Media — tab availability (hide-nothing)")

        T.ok("Available is available", MediaAvailability.Available.isAvailable)
        T.ok("EditorUnsupported not available", !MediaAvailability.EditorUnsupported("image/gif").isAvailable)
        T.ok("NoApiKey not available", !MediaAvailability.NoApiKey.isAvailable)
        T.ok("Offline not available", !MediaAvailability.Offline.isAvailable)

        // Gboard ka verbatim toast (SO: "Gboard: Enable GIF insertion on EditText")
        T.eq("EditorUnsupported EN = Gboard verbatim",
            MediaAvailability.EditorUnsupported("image/gif").reason(false),
            "The text field does not support GIF insertion from the keyboard")
        T.ok("EditorUnsupported HI maujood",
            MediaAvailability.EditorUnsupported("image/gif").reason(true)!!.isNotEmpty())

        T.ok("NoApiKey reason mein 'Klipy' (setup hint)",
            MediaAvailability.NoApiKey.reason(false)!!.contains("Klipy"))
        T.ok("NoApiKey HI mein bhi hint",
            MediaAvailability.NoApiKey.reason(true)!!.contains("Klipy"))
        T.ok("NoApiKey reason bundled pack ka zikr karta hai (feature chhupta nahi)",
            MediaAvailability.NoApiKey.reason(true)!!.contains("sticker"))

        T.eq("Offline reason = Gboard verbatim",
            MediaAvailability.Offline.reason(false),
            "Can't use this tool at the moment. Please try again later.")
        T.eq("Available → koi reason nahi", MediaAvailability.Available.reason(true), null)
    }
}
