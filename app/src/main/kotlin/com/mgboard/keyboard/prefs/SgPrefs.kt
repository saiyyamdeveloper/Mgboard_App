package com.mgboard.keyboard.prefs

import android.content.Context
import android.content.SharedPreferences
import com.mgboard.keyboard.model.SgDefaults

/**
 * Settings storage — Mgboard_Web ke `storageGet/storageSet/sgGetBool/sgSetBool`
 * ka Android port.
 *
 * Parity rule: **same key names, same string encoding ('1'/'0'), same defaults**
 * jaisa web mein hai, taaki dono apps ka behaviour ek jaisa rahe aur future mein
 * export/import (JSON) bhi possible ho.
 */
class SgPrefs(context: Context) {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Web parity: booleans '1'/'0' string bankar store hoti hain. */
    private fun raw(key: String): String? = if (sp.contains(key)) sp.getString(key, null) else null
    private fun putRaw(key: String, value: String) = sp.edit().putString(key, value).apply()

    // ── booleans (Gboard defaults) ────────────────────────────────────────────

    fun getBool(key: String): Boolean {
        val r = raw(key) ?: return SgDefaults.BOOL[key] ?: false
        return r == "1"
    }

    /** Side effects ke bina likhta hai (web: `sgSetBoolRaw`). */
    fun setBoolRaw(key: String, value: Boolean) { putRaw(key, if (value) "1" else "0"); notifyChanged() }

    fun setBool(key: String, value: Boolean) {
        setBoolRaw(key, value)
        applySideEffects(key, value)
    }

    /**
     * Gboard-exact side effects + mutual exclusion.
     * Source: mgboard-setting-project/FUNCTIONS-AND-LOGIC.md §0.2 / §2.4
     * (web: sgApplySideEffects — identical case list)
     */
    private fun applySideEffects(key: String, value: Boolean) {
        when (key) {
            // "This hides the language switch key."
            "sg_show_emoji_key" -> if (value) setBoolRaw("sg_show_lang_key", false)
            // "This hides the emoji key."
            "sg_show_lang_key"  -> if (value) setBoolRaw("sg_show_emoji_key", false)
            // sg_toolbar (web: toolbar row hide) aur sg_battery_saver_theme /
            // sg_incognito (web: toast) IME service port ke saath wire honge.
        }
    }

    // ── sliders ───────────────────────────────────────────────────────────────

    /** Keyboard height ratio — Gboard: "between 0.5 and 2.0, inclusive". */
    fun getHeight(): Double = clamp(raw(KEY_HEIGHT)?.toDoubleOrNull() ?: 1.0, 0.5, 2.0)

    fun setHeight(v: Double) {
        val n = clamp(v, 0.5, 2.0)
        putRaw(KEY_HEIGHT, n.toString())
        notifyChanged()
    }

    /** Access-point icons on the suggestion strip — Gboard range 3–8. */
    fun getPinCount(): Int = clamp(raw(KEY_PIN)?.toIntOrNull() ?: 5, 3, 8)

    fun setPinCount(v: Int) { putRaw(KEY_PIN, clamp(v, 3, 8).toString()); notifyChanged() }

    // ── UI language (settings labels EN/HI) ───────────────────────────────────

    /** null = follow device locale (web: keyboard mode se decide hota tha). */
    fun getUiLang(): String? = raw(KEY_LANG)
    fun setUiLang(v: String?) {
        if (v == null) sp.edit().remove(KEY_LANG).apply() else putRaw(KEY_LANG, v)
        notifyChanged()
    }

    fun hindiActive(deviceHindi: Boolean): Boolean =
        when (getUiLang()) { "hi" -> true; "en" -> false; else -> deviceHindi }

    // ── data reset ────────────────────────────────────────────────────────────

    /** Web ke `sg_*` keys ko defaults par le jaata hai (Gboard "Reset settings"). */
    fun resetSettingsToDefaults() {
        val e = sp.edit()
        sp.all.keys.filter { it.startsWith("sg_") && it != KEY_LANG }.forEach { e.remove(it) }
        e.apply()
        notifyChanged()
    }

    // ── theme / one-handed (web: applyTheme, getOneHanded) ────────────────────

    fun getTheme(): String = raw(KEY_THEME) ?: "system"
    fun setTheme(v: String) { putRaw(KEY_THEME, v); notifyChanged() }

    /** '' | 'left' | 'right' — web: getOneHanded() invalid values ko '' banata hai. */
    fun getOneHanded(): String {
        val v = raw(KEY_ONE_HANDED)
        return if (v == "left" || v == "right") v else ""
    }

    fun setOneHanded(v: String) {
        putRaw(KEY_ONE_HANDED, if (v == "left" || v == "right") v else "")
        notifyChanged()
    }

    /** off → right → left → off (web: cycleOneHanded) */
    fun cycleOneHanded(): String {
        val next = when (getOneHanded()) { "" -> "right"; "right" -> "left"; else -> "" }
        setOneHanded(next)
        return next
    }

    /** Toolbar/suggestion strip dikhani hai ya nahi (web: sg_toolbar side effect). */
    fun toolbarVisible(): Boolean = getBool("sg_toolbar")

    // ── live update: settings badalte hi keyboard turant refresh ───────────────
    // (padding-project requirement: "live update without restart")

    private val listeners = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()

    fun addListener(l: () -> Unit) { listeners.add(l) }
    fun removeListener(l: () -> Unit) { listeners.remove(l) }
    private fun notifyChanged() { listeners.forEach { runCatching { it() } } }

    private fun clamp(v: Double, lo: Double, hi: Double) = if (v < lo) lo else if (v > hi) hi else v
    private fun clamp(v: Int, lo: Int, hi: Int) = if (v < lo) lo else if (v > hi) hi else v

    companion object {
        const val FILE = "mg_settings"

        // ── Web (localStorage) ke same key names ──
        const val KEY_HEIGHT      = "mg_keyboard_height_ratio"
        const val KEY_ONE_HANDED  = "mg_one_handed_mode"
        const val KEY_PIN_CAPACITY = "mg_pin_capacity"
        const val KEY_PD          = "mg_personal_dictionary"
        const val KEY_CLIPBOARD   = "mg_clipboard_history"
        const val KEY_HAPTIC      = "mg_haptic_enabled"

        const val KEY_THEME       = "mg_theme"            // web: applyTheme(dark/light/amoled)

        // ── Android-specific UI key ──
        const val KEY_LANG        = "mg_settings_lang"
    }
}
