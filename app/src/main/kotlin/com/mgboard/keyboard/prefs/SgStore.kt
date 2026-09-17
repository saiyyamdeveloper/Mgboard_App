package com.mgboard.keyboard.prefs

import android.content.Context
import org.json.JSONArray

/**
 * Web ke `cpLoad/cpSave` aur `loadPd/savePd` ka Android equivalent —
 * same SharedPreferences file, same JSON shape, same key names.
 *
 * Isse Settings ke "Clear clipboard history" / "Delete learned words" /
 * "Icons count" rows abhi bhi asli kaam karte hain (IME service se pehle).
 */
object SgStore {

    private fun sp(c: Context) =
        c.applicationContext.getSharedPreferences(SgPrefs.FILE, Context.MODE_PRIVATE)

    // ── clipboard history: [{text, ts}] ───────────────────────────────────────

    fun clipboardCount(c: Context): Int = parseArray(sp(c).getString(SgPrefs.KEY_CLIPBOARD, null)).length()

    /** Web: `cpSave([])` — history poori tarah saaf. */
    fun clearClipboard(c: Context) = sp(c).edit().putString(SgPrefs.KEY_CLIPBOARD, "[]").apply()

    /**
     * Clipboard history — web ke `cpLoad()` jaisa `[{text, ts}]` shape.
     * Toolbar project ka Clipboard panel isi ko padhta hai (§5.3).
     */
    data class ClipEntry(val text: String, val ts: Long)

    fun clipboardHistory(c: Context, limit: Int = 50): List<ClipEntry> {
        val a = parseArray(sp(c).getString(SgPrefs.KEY_CLIPBOARD, null))
        val out = ArrayList<ClipEntry>(a.length())
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            val t = o.optString("text", "")
            if (t.isEmpty()) continue
            out += ClipEntry(t, o.optLong("ts", 0L))
        }
        return out.sortedByDescending { it.ts }.take(limit)
    }

    /** Naya clip add karo — duplicate ho to uska ts refresh (web: cpUnshift jaisa). */
    fun addClipboard(c: Context, text: String, limit: Int = 50) {
        if (text.isBlank()) return
        val now = System.currentTimeMillis()
        val next = ArrayList<ClipEntry>()
        next += ClipEntry(text, now)
        clipboardHistory(c, limit).forEach { if (it.text != text) next += it }
        val a = JSONArray()
        next.take(limit).forEach {
            a.put(org.json.JSONObject().put("text", it.text).put("ts", it.ts))
        }
        sp(c).edit().putString(SgPrefs.KEY_CLIPBOARD, a.toString()).apply()
    }

    fun removeClipboard(c: Context, text: String) {
        val a = JSONArray()
        clipboardHistory(c).filter { it.text != text }.forEach {
            a.put(org.json.JSONObject().put("text", it.text).put("ts", it.ts))
        }
        sp(c).edit().putString(SgPrefs.KEY_CLIPBOARD, a.toString()).apply()
    }

    // ── personal dictionary: [{w, s, t}] ──────────────────────────────────────

    fun pdCount(c: Context): Int = parseArray(sp(c).getString(SgPrefs.KEY_PD, null)).length()

    /** Web: learned words delete karna. */
    fun clearPd(c: Context) = sp(c).edit().putString(SgPrefs.KEY_PD, "[]").apply()

    // ── pinned icons (access points) ──────────────────────────────────────────

    fun pinnedIds(c: Context): List<String> {
        val a = parseArray(sp(c).getString(SgPrefs.KEY_PINNED, null))
        return (0 until a.length()).mapNotNull { a.optString(it) }
    }

    fun savePinnedIds(c: Context, ids: List<String>) {
        val a = JSONArray()
        ids.forEach { a.put(it) }
        sp(c).edit().putString(KEY_PINNED, a.toString()).apply()
    }

    /** Web jaisa: capacity girti hai to extra pinned ids trim ho jaate hain. */
    fun trimPinnedTo(c: Context, capacity: Int) {
        val ids = pinnedIds(c)
        if (ids.size > capacity) {
            val a = JSONArray()
            ids.take(capacity).forEach { a.put(it) }
            sp(c).edit().putString(SgPrefs.KEY_PINNED, a.toString()).apply()
        }
    }

    const val KEY_PINNED = "mg_pinned_ext_ids"

    private fun parseArray(s: String?): JSONArray =
        if (s.isNullOrBlank()) JSONArray() else try { JSONArray(s) } catch (e: Exception) { JSONArray() }
}
