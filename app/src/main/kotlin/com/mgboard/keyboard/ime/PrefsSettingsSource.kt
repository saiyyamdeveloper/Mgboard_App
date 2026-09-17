package com.mgboard.keyboard.ime

import android.content.Context
import android.content.res.Configuration
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import com.mgboard.keyboard.grid.GridMenu
import com.mgboard.keyboard.prefs.SgPrefs
import com.mgboard.keyboard.prefs.SgStore
import com.mgboard.keyboard.ui.SgText

/**
 * [KeyboardModel.SettingsSource] ka real implementation — SharedPreferences
 * (`SgPrefs`) se values deta hai.
 *
 * Storage keys web ke localStorage keys ke **same** hain, isliye defaults aur
 * behaviour dono apps mein identical rehte hain (parity checker isi ko verify
 * karta hai).
 */
class PrefsSettingsSource(
    private val context: Context,
    private val prefs: SgPrefs,
    private val openSettings: () -> Unit,
) : KeyboardModel.SettingsSource {

    /** Tests/preview ke liye orientation force karne ka hook. */
    var landscapeOverride: Boolean? = null

    override val landscape: Boolean
        get() = landscapeOverride
            ?: (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)

    override val storedCapacity: Int?
        get() {
            val raw = context.getSharedPreferences(SgPrefs.FILE, Context.MODE_PRIVATE)
                .getString(GridMenu.KEY_CAPACITY, null)
            // web: raw null/'' ya parseInt NaN ho to orientation default chalta hai
            if (raw.isNullOrBlank()) return null
            return raw.toIntOrNull()
        }

    override val heightRatio: Double get() = prefs.getHeight()
    override val oneHanded: String get() = prefs.getOneHanded()
    override val toolbarVisible: Boolean get() = prefs.toolbarVisible()
    override val theme: String get() = prefs.getTheme()
    override val hapticEnabled: Boolean get() = prefs.getBool(SgPrefs.KEY_HAPTIC)
    override val pinnedIds: List<String> get() = SgStore.pinnedIds(context)

    override val uiHindi: Boolean get() = prefs.hindiActive(SgText.isDeviceHindi(context))

    override fun setPinnedIds(ids: List<String>) = SgStore.savePinnedIds(context, ids)

    override fun cycleOneHanded(): String = prefs.cycleOneHanded()

    override fun setOneHanded(mode: String) = prefs.setOneHanded(mode)

    override fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    override fun onSettingsChanged() = openSettings()
}

/** EditorInfo ke action se Enter key ka label (Gboard jaisa contextual label). */
fun enterLabelFor(info: EditorInfo?): String =
    when (info?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)) {
        EditorInfo.IME_ACTION_GO -> "Go"
        EditorInfo.IME_ACTION_SEARCH -> "🔍"
        EditorInfo.IME_ACTION_SEND -> "Send"
        EditorInfo.IME_ACTION_NEXT -> "Next"
        EditorInfo.IME_ACTION_DONE -> "Done"
        else -> "⏎"
    }
