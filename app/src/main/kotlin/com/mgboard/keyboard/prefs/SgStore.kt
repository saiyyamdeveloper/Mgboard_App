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
