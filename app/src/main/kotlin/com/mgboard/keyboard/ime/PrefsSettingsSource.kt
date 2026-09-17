package com.mgboard.keyboard.ime

import android.content.Context
import android.content.res.Configuration
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import com.mgboard.keyboard.BuildConfig
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

    /** IME service editor actions ke liye inject karta hai (edit menu / IME action). */
    var editorActions: EditorActions? = null

    /** IME service 🌐 access point ke liye system picker inject karta hai. */
    var imePicker: (() -> Unit)? = null

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

    // ══════════════ toolbar-project (Gboard keyboard toolbar) ══════════════

    private val toolbarSp
        get() = context.getSharedPreferences(SgPrefs.FILE, Context.MODE_PRIVATE)

    override val toolbarFlags: com.mgboard.keyboard.toolbar.ToolbarFlags
        get() {
            // Gboard ke APK config keys ke MgBoard equivalents — defaults research §2 se
            val sp = toolbarSp
            return com.mgboard.keyboard.toolbar.ToolbarFlags(
                showToolbar = toolbarVisible(),
                imeActionAccessPoint = sp.getBoolean(KEY_IME_ACTION_AP, true),
                imeSwitchAccessPoint = sp.getBoolean(KEY_IME_SWITCH_AP, false),
                undoRedoViaAccessPoint = sp.getBoolean(KEY_UNDO_REDO_AP, true),
                writingToolsInStrip = sp.getBoolean(KEY_WRITING_TOOLS_STRIP, false),
                emojiKeyInToolbar = sp.getBoolean(KEY_EMOJI_KEY_TOOLBAR, true),
                accessPointCustomization = sp.getBoolean(KEY_AP_CUSTOMIZATION, true),
                accessPointEducationFooter = sp.getBoolean(KEY_AP_EDUCATION, true),
                autoHideKeyboardHeader = sp.getBoolean(KEY_AUTO_HIDE_HEADER, false),
                suggestionStripPopupMenu = sp.getBoolean(KEY_STRIP_POPUP_MENU, true),
                clipboardContentSuggestion = sp.getBoolean(KEY_CLIPBOARD_SUGGESTION, true),
                stripTrailingSpaceDivider = sp.getBoolean(KEY_TRAILING_SPACE_DIVIDER, true),
                forceHorizontalOnFoldables = sp.getBoolean(KEY_FORCE_HORIZONTAL_FOLDABLE, false),
                quickInsert = sp.getBoolean(KEY_QUICK_INSERT, false),
            )
        }

    override val toolbarOrderRaw: String?
        get() = toolbarSp.getString(KEY_ACCESS_POINTS_ORDER, null)

    override val hasImeAction: Boolean get() = editorActions?.hasImeAction() ?: true
    override val isEditingExistingText: Boolean get() = editorActions?.isEditingExistingText() ?: false

    override fun performImeAction() { editorActions?.performImeAction() }
    override fun showImePicker() { imePicker?.invoke() }

    override fun clipboardHistory(): List<String> =
        SgStore.clipboardHistory(context).map { it.text }

    override fun addClipboardEntry(text: String) = SgStore.addClipboard(context, text)
    override fun removeClipboardEntry(text: String) = SgStore.removeClipboard(context, text)

    override fun recentSymbols(): List<String> = readList(KEY_RECENT_SYMBOLS)
    override fun rememberRecentSymbol(sym: String) = pushList(KEY_RECENT_SYMBOLS, sym, 40)
    override fun recentEmoji(): List<String> = readList(KEY_RECENT_EMOJI)
    override fun rememberRecentEmoji(e: String) = pushList(KEY_RECENT_EMOJI, e, 40)
    override fun favoriteEmoji(): List<String> = readList(KEY_FAVORITE_EMOJI)

    override fun editorSelectAll() { editorActions?.selectAll() }
    override fun editorCopy() { editorActions?.copy() }
    override fun editorCut() { editorActions?.cut() }
    override fun editorPaste() { editorActions?.paste() }

    override fun setToolbarVisible(v: Boolean) = prefs.setBool(SgPrefs.KEY_TOOLBAR, v)

    // ── GIF / Stickers (Commit Content API + Klipy) ─────────────────────────
    private val mediaCommit by lazy { com.mgboard.keyboard.media.MediaCommitController(context) }

    override fun mediaEditorSupports(mime: String): Boolean =
        mediaCommit.editorSupports(currentEditorInfo(), mime)

    override fun commitBundledSticker(sticker: com.mgboard.keyboard.media.BundledSticker): Boolean =
        mediaCommit.commitBundledSticker(sticker, currentEditorInfo(), currentInputConnection())

    override fun commitRemoteMedia(item: com.mgboard.keyboard.media.MediaItem): Boolean =
        mediaCommit.commitRemoteMedia(item, currentEditorInfo(), currentInputConnection())

    /**
     * Klipy key: runtime override (Settings) phir BuildConfig default.
     * Repo mein key commit NAHI hoti — `local.properties`/gradle property se aati hai
     * (`KLIPY_APP_KEY`), warna panel par setup hint dikhta hai (placeholder mode).
     */
    override fun klipyAppKey(): String =
        toolbarSp.getString(KEY_KLIPY_KEY, null)?.takeIf { it.isNotBlank() }
            ?: BuildConfig.KLIPY_APP_KEY

    override fun setKlipyAppKey(key: String) {
        toolbarSp.edit().putString(KEY_KLIPY_KEY, key.trim()).apply()
    }

    override fun klipyFetch(
        url: String,
        kind: com.mgboard.keyboard.media.KlipyApi.Kind,
        onResult: (com.mgboard.keyboard.media.MediaPage?) -> Unit,
    ) {
        Thread {
            val page = try {
                val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 12000
                conn.setRequestProperty("User-Agent", "MgBoard-Keyboard/1.0")
                val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                com.mgboard.keyboard.media.KlipyApi.parsePage(body, kind)
            } catch (e: Exception) {
                null
            }
            android.os.Handler(android.os.Looper.getMainLooper()).post { onResult(page) }
        }.start()
    }

    /** IME service current EditorInfo/InputConnection inject karta hai. */
    var currentEditorInfo: () -> android.view.inputmethod.EditorInfo? = { null }
    var currentInputConnection: () -> android.view.inputmethod.InputConnection? = { null }

    // ── translate (ML Kit on-device, hi ↔ en) ───────────────────────────────
    /**
     * ML Kit engine. `by lazy` isliye taaki keyboard khulte hi ML Kit classes load
     * na hon — sirf jab user translate panel khole.
     */
    private val mlKitEngine by lazy { MlKitTranslateEngine(wifiOnly = translateWifiOnly) }

    override fun translateEngine(): com.mgboard.keyboard.translate.TranslateEngine = mlKitEngine

    override val translateWifiOnly: Boolean
        get() = toolbarSp.getBoolean(KEY_TRANSLATE_WIFI_ONLY, true)

    override fun translateSrcCode(): String? =
        toolbarSp.getString(KEY_TRANSLATE_SRC, null)?.takeIf { it.isNotBlank() }

    override fun translateTgtCode(): String? =
        toolbarSp.getString(KEY_TRANSLATE_TGT, null)?.takeIf { it.isNotBlank() }

    override fun saveTranslateLanguages(src: String, tgt: String) {
        toolbarSp.edit().putString(KEY_TRANSLATE_SRC, src).putString(KEY_TRANSLATE_TGT, tgt).apply()
    }

    override fun scheduleTimer(delayMs: Long, runnable: () -> Unit): Long {
        val token = ++timerToken
        timers[token] = runnable
        timerHandler.postDelayed({
            val r = timers.remove(token)
            r?.invoke()
        }, delayMs)
        return token
    }

    override fun cancelTimer(token: Long) {
        timers.remove(token)     // handler callback aane par runnable milega hi nahi
    }

    private val timerHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val timers = HashMap<Long, () -> Unit>()
    private var timerToken = 0L

    private fun readList(key: String): List<String> =
        toolbarSp.getString(key, "")?.split("|")?.filter { it.isNotEmpty() } ?: emptyList()

    private fun pushList(key: String, value: String, limit: Int) {
        val next = (listOf(value) + readList(key)).distinct().take(limit)
        toolbarSp.edit().putString(key, next.joinToString("|")).apply()
    }

    companion object {
        // Gboard ke APK config-key names ke MgBoard storage keys
        const val KEY_ACCESS_POINTS_ORDER = "access_points_showing_order"
        const val KEY_IME_ACTION_AP = "enable_ime_action_access_point"
        const val KEY_IME_SWITCH_AP = "enable_ime_switch_access_point"
        const val KEY_UNDO_REDO_AP = "enable_undo_redo_via_access_point"
        const val KEY_WRITING_TOOLS_STRIP = "enable_writing_tools_icon_in_suggestion_strip"
        const val KEY_EMOJI_KEY_TOOLBAR = "enable_show_emoji_key_in_horizontal_toolbar"
        const val KEY_AP_CUSTOMIZATION = "enable_access_point_customization"
        const val KEY_AP_EDUCATION = "enable_access_point_education_footer"
        const val KEY_AUTO_HIDE_HEADER = "enable_auto_hide_keyboard_header"
        const val KEY_STRIP_POPUP_MENU = "enable_suggestion_strip_popup_menu"
        const val KEY_CLIPBOARD_SUGGESTION = "enable_clipboard_content_suggestion"
        const val KEY_TRAILING_SPACE_DIVIDER = "enable_strip_trailing_space_divider"
        const val KEY_FORCE_HORIZONTAL_FOLDABLE = "force_enable_horizontal_toolbar_on_foldables"
        const val KEY_QUICK_INSERT = "enable_quick_insert"

        const val KEY_KLIPY_KEY = "mg_klipy_app_key"

        const val KEY_TRANSLATE_SRC = "translate_source_language"
        const val KEY_TRANSLATE_TGT = "translate_target_language"
        const val KEY_TRANSLATE_WIFI_ONLY = "translate_download_wifi_only"

        const val KEY_RECENT_SYMBOLS = "mg_recent_symbols"
        const val KEY_RECENT_EMOJI = "mg_recent_emoji"
        const val KEY_FAVORITE_EMOJI = "mg_favorite_emoji"
    }
}

/** Editor par chalne wale actions — IME service implement karta hai. */
interface EditorActions {
    fun hasImeAction(): Boolean
    fun isEditingExistingText(): Boolean
    fun performImeAction()
    fun selectAll()
    fun copy()
    fun cut()
    fun paste()
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
