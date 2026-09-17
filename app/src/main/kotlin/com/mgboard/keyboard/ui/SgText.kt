package com.mgboard.keyboard.ui

import android.content.Context
import com.mgboard.keyboard.R
import com.mgboard.keyboard.model.SgItem
import com.mgboard.keyboard.model.SgItems
import com.mgboard.keyboard.model.SgPage
import com.mgboard.keyboard.model.SgType
import com.mgboard.keyboard.prefs.SgPrefs
import com.mgboard.keyboard.prefs.SgStore

/**
 * Localization helper — web ke `sgHiActive()/sgLbl()/sgSum()` ka 1:1 port.
 *
 * Web par label language keyboard mode se decide hoti thi (hindi/gondi → HI).
 * Android par abhi keyboard service nahi hai, isliye device locale default hai
 * aur user Settings top bar se EN/HI manually switch kar sakta hai
 * (choice `mg_settings_lang` mein persist hoti hai).
 */
class SgText(private val ctx: Context, private val prefs: SgPrefs) {

    val hi: Boolean = prefs.hindiActive(isDeviceHindi(context))

    fun page(p: SgPage): String = if (hi) p.hi else p.en
    fun label(it: SgItem): String = if (hi) it.hi else it.en

    fun summary(it: SgItem): String? {
        val s = if (hi) (it.sumHi ?: it.sumEn) else (it.sumEn ?: it.sumHi)
        return s?.takeIf { it.isNotBlank() }
    }

    fun str(id: Int, vararg args: Any?): String =
        if (args.isEmpty()) ctx.getString(id) else ctx.getString(id, *args)

    /** "Needs the MgBoard keyboard service (coming next milestone)." */
    fun pending(feature: String): String = str(R.string.sg_pending, feature)

    val searchHint: String get() = str(R.string.sg_search_hint)
    val unavailable: String get() = str(R.string.sg_unavailable)

    companion object {
        fun isDeviceHindi(c: Context): Boolean {
            val l = c.resources.configuration.locales.get(0)
            return l.language.equals("hi", true) || l.language.equals("mr", true)
        }

        /** Debug page ke dynamic info rows — web ke `dynamic()` ka Android version. */
        fun dynamicValue(context: Context, id: String): String? = when (id) {
            "sg_dbg_gates" -> {
                val n = SgItems.ALL.count { it.type == SgType.GATED }
                "$n gated tiles"
            }
            "sg_dbg_capacity" -> {
                val p = SgPrefs(context)
                val pinned = SgStore.pinnedIds(context).size
                "capacity: ${p.getPinCount()} · pinned: $pinned"
            }
            "sg_learned" -> "${SgStore.pdCount(context)} words"
            "sg_cp_retention" -> "Clipboard history: ${SgStore.clipboardCount(context)} entries"
            "sg_dbg_order" -> null   // web: extOrderSemicolon() — IME port ke baad
            else -> null
        }
    }
}
