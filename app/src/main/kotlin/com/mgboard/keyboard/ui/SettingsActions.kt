package com.mgboard.keyboard.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.mgboard.keyboard.R
import com.mgboard.keyboard.model.SgItem
import com.mgboard.keyboard.model.SgType
import com.mgboard.keyboard.prefs.SgPrefs
import com.mgboard.keyboard.prefs.SgStore

/**
 * ACTION / INFO / GATED rows ka behaviour.
 *
 * Principle (web ke jaisa hi): **kuch bhi hide nahi karte.** Jo feature Android
 * par abhi possible nahi (kyunki MgBoard IME service abhi port nahi hua), us row
 * par tap karne se saaf message milta hai — Gboard ke gated items ki tarah.
 * GATED rows hamesha Gboard ka **verbatim reason string** dikhate hain.
 */
class SettingsActions(
    private val context: Context,
    private val prefs: SgPrefs,
    private val t: SgText,
) {

    /** @return true agar row ne koi kaam kiya (UI refresh chahiye). */
    fun onTap(it: SgItem): Boolean = when (it.type) {
        SgType.GATED -> { toast(it.gate ?: t.unavailable); false }
        SgType.ACTION -> runAction(it)
        else -> false
    }

    private fun runAction(it: SgItem): Boolean = when (it.id) {

        // ── abhi Android par ASLI kaam karne wale ────────────────────────────
        "sg_cp_clear" -> {
            SgStore.clearClipboard(context)
            toast(context.getString(R.string.sg_cp_cleared))
            true
        }
        "sg_delete_learned" -> {
            SgStore.clearPd(context)
            toast(context.getString(R.string.sg_learned_deleted))
            true
        }
        "sg_delete_data" -> {
            prefs.resetSettingsToDefaults()
            toast(context.getString(R.string.sg_settings_reset))
            true
        }
        "sg_help_open" -> { openUrl(URL_REPO); true }
        "sg_about_open" -> {
            toast(context.getString(R.string.sg_about_text))
            true
        }

        // ── IME service port ke baad wire honge (abhi honest pending message) ─
        "sg_one_handed"       -> pending("One-handed keyboard")
        "sg_theme_picker"     -> pending("Theme picker")
        "sg_next_lang"        -> pending("Language switching")
        "sg_clear_corrected"  -> pending("Cleared-words history")
        "sg_voice"            -> pending("Voice typing")
        "sg_emoji_panel"      -> pending("Emoji panel")
        "sg_cp_open"          -> pending("Clipboard history panel")
        "sg_pd"               -> pending("Personal dictionary")
        "sg_symbols"          -> pending("Symbols mode")
        "sg_customize_menus"  -> pending("Toolbar customization")
        "sg_dbg_apply_order"  -> pending("Access point order")

        else -> { toast(t.unavailable); false }
    }

    private fun pending(feature: String): Boolean { toast(t.pending(feature)); return false }

    private fun openUrl(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            toast(url)
        }
    }

    private fun toast(msg: String) {
        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
    }

    companion object {
        const val URL_REPO = "https://github.com/saiyyamdeveloper/Mgboard_Web"
        const val URL_LIVE = "https://saiyyamdeveloper.github.io/Mgboard_Web/"
    }
}
