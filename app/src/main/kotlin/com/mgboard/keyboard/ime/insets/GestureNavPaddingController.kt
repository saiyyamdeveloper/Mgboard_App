package com.mgboard.keyboard.ime.insets

import android.content.Context
import android.content.res.Configuration
import android.database.ContentObserver
import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.annotation.ColorInt
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.max
import kotlin.math.min

/**
 * Mgboard — Gesture-navigation bottom safe-area padding (**Gboard-exact**, APK से verified).
 *
 *  - swipe/gesture navigation ON  → आख़िरी key-row (spacebar row) के नीचे system की gap जितनी
 *                                   extra padding, keyboard के background color में painted
 *                                   (continuous strip — कोई border/alag color नहीं)
 *  - 3-button navigation ON       → कोई *visible* extra padding नहीं
 *  - height हमेशा WindowInsets से  → कोई hardcoded/fixed value नहीं (floor भी default 0 है)
 *  - mode live switch होने पर layout खुद adjust होता है (IME restart नहीं)
 *
 * ── Gboard APK (18.3.1-beta) से मिली असली internal keys ─────────────────────────────
 *   normal_mode_keyboard_bottom_gap_portrait          ← यही feature है
 *   normal_mode_keyboard_bottom_gap_landscape
 *   normal_mode_decor_view_stable_inset_bottom_portrait   ← height का source
 *   normal_mode_decor_view_stable_inset_bottom_landscape
 * नाम से ही तीन बातें पक्की होती हैं:
 *   (a) gap सिर्फ़ **"normal mode"** में है → floating / split / one-handed में नहीं
 *   (b) height **DecorView के stable bottom inset** से आती है → `getInsetsIgnoringVisibility(navigationBars())`
 *   (c) **portrait और landscape के अलग values** हैं
 *
 * और एक हैरान करने वाली बात: Gboard के APK में nav-mode detection की **कोई** flag/key नहीं है
 * (न `config_navBarInteractionMode`, न `navigation_mode`)। Gboard बस stable inset उठा लेता है —
 * 3-button mode में gap अपने-आप छिप जाती है क्योंकि वहाँ nav bar opaque होकर ऊपर draw होता है।
 * हम mode-gate इसलिए रखते हैं क्योंकि (1) तुम्हारी requirement यही कहती है और
 * (2) Android 15 (API 35) में `systemGestures.bottom` 3-button mode में भी non-zero आता है,
 *     इसलिए Gboard वाला "सीधे उठा लो" तरीक़ा वहाँ धोखा दे सकता है।
 *
 * ── यह क्यों काम करता है (AOSP android14-release से verified) ───────────────────────
 * 1) IME window की frame हमेशा screen के bottom तक जाती है:
 *      InputMethodService.onCreate → lp.setFitInsetsSides(Side.all() & ~Side.BOTTOM)
 *      WindowLayout.computeFrames  → "The IME can always extend under the bottom cutout
 *                                     if the navbar is there."
 *    Gboard खुद कहता है: "…extend the IME window to fullscreen… the keyboard UI might be
 *    cut off by the cutout area if keyboard paddings are not set properly."
 * 2) 3-button mode में nav bar opaque system window है जो IME के ऊपर draw होता है → gap छिप जाती है।
 *    Gesture mode में nav bar transparent है (सिर्फ़ pill) → keys को खुद ऊपर उठना पड़ता है।
 * 3) framework खुद IME window पर system-gesture insets पढ़ता है:
 *      InputMethodService.setImeExclusionRect() → rootView.getRootWindowInsets()
 *                                                  .getInsets(Type.systemGestures())
 * 4) IME को अपने आप के insets कभी नहीं मिलते (Type.ime() == 0):
 *      InsetsPolicy.enforceInsetsPolicyForTarget() → state.removeSource(ID_IME)
 *
 * ⚠️ GOTCHA: default IME window में decorFitsSystemWindows == true रहता है (API ≤ 34), तब DecorView
 * insets consume कर लेता है और listener को bottom ≈ 0 मिलता है। इसलिए [attach] में हम उसे false
 * करते हैं — और इसीलिए 3-button branch **adaptive** है (नीचे [applyInsets] देखो), वरना
 * कुछ devices पर padding दोगुनी हो जाती।
 */
class GestureNavPaddingController(
    private val ime: InputMethodService,
    /** keyboard का असली background color — padding strip इसी से paint होगी। */
    @ColorInt private var keyboardBackgroundColor: Int = Color.WHITE,
) {

    /** जिस view पर padding लगेगी — पूरा keyboard container/root सबसे आसान है। */
    private var targetView: View? = null

    /**
     * keyboard screen के bottom पर docked है? (Gboard: `normal_mode_` scoping)
     * floating / split / one-handed mode में `false` लौटाओ → gap 0 हो जाएगी।
     */
    var isKeyboardDockedAtBottom: () -> Boolean = { true }

    /** padding बदलने पर callback — height/theme/preview re-sync करने के लिए। */
    var onPaddingChanged: ((bottom: Int, left: Int, right: Int) -> Unit)? = null

    /**
     * gap की कम-से-कम height। **Default 0 = Gboard-exact** (Gboard device inset की इज़्ज़त करता है,
     * अपना कुछ नहीं जोड़ता)। कुछ OEM insets 0 देते हैं, तब चाहो तो यहाँ 8–12dp दे सकते हो।
     */
    var minGapPx: Int = 0

    /**
     * Safety cap — यह layout choice नहीं, सिर्फ़ **garbage-value guard** है (desktop windowing /
     * अजीब OEM insets से बचाने के लिए)। Gboard में ऐसा cap नहीं है; 0 = cap बंद।
     */
    var maxGapPx: Int = 0

    /** landscape के लिए अलग floor/cap चाहिए तो बदलो (Gboard: `*_bottom_gap_landscape`)। */
    var minGapPxLandscape: Int? = null
    var maxGapPxLandscape: Int? = null

    /** verbose logging (अपनी BuildConfig.DEBUG से wire कर लो)। */
    var debugLog: Boolean = false

    private var decorView: View? = null
    private var imeWindow: Window? = null

    // base paddings एक बार store करो — inset हमेशा "base + inset" absolute set होगा,
    // क्योंकि listener बार-बार fire होता है और `+=` से padding accumulate हो जाती है।
    private var basePadLeft = 0
    private var basePadTop = 0
    private var basePadRight = 0
    private var basePadBottom = 0

    private var lastBottom = -1
    private var lastLeft = -1
    private var lastRight = -1

    private var gestureNav = false
    private var savedNavBarColor: Int? = null

    private val handler = Handler(Looper.getMainLooper())

    /**
     * Settings.Secure "navigation_mode" पर नज़र रखता है — keyboard छिपी हुई हो तब भी
     * mode switch का पता चल जाता है (तब insets dispatch नहीं होते)।
     */
    private val navModeObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            if (debugLog) Log.d(TAG, "navigation_mode changed → reapply")
            reapply()
        }
    }

    // ───────────────────────────── lifecycle ─────────────────────────────

    /** MgboardImeService.onCreate() में, input view बनने के बाद call करो। */
    fun attach(rootView: View) {
        targetView = rootView
        basePadLeft = rootView.paddingLeft
        basePadTop = rootView.paddingTop
        basePadRight = rootView.paddingRight
        basePadBottom = rootView.paddingBottom

        val window = ime.window?.window ?: return
        imeWindow = window
        decorView = window.decorView

        makeImeWindowEdgeToEdge(window)

        // ★ dynamic insets listener — nav mode / rotation / OEM setting बदलते ही fire होता है
        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { _, insets ->
            applyInsets(insets)
            insets // CONSUMED मत लौटाओ — बच्चों तक insets जाने दो
        }

        registerNavModeObserver()
        requestInsets()
    }

    /** onWindowShown() में call करो। */
    fun onWindowShown() {
        gestureNav = isGestureNavigation()
        applyNavBarAppearance()
        requestInsets()
    }

    /** onStartInputView() में call करो (हर नई editor पर sync)। */
    fun onStartInputView() = requestInsets()

    /**
     * onConfigurationChanged() में call करो — RRO overlay बदलने पर config change आता है।
     * Gboard के पास portrait/landscape के अलग gap values हैं, इसलिए यहाँ orientation भी देखते हैं।
     */
    fun onConfigurationChanged(newConfig: Configuration? = null) {
        gestureNav = isGestureNavigation()
        applyNavBarAppearance()
        requestInsets()
    }

    /** onDestroy() में call करो। */
    fun detach() {
        runCatching { ime.contentResolver.unregisterContentObserver(navModeObserver) }
        decorView?.let { ViewCompat.setOnApplyWindowInsetsListener(it, null) }
        restoreNavBarColor()
        decorView = null
        imeWindow = null
        targetView = null
        lastBottom = -1; lastLeft = -1; lastRight = -1
    }

    /** theme बदलने पर (day/night / user theme) call करो। */
    fun setKeyboardBackgroundColor(@ColorInt color: Int) {
        if (keyboardBackgroundColor == color) return
        keyboardBackgroundColor = color
        applyNavBarAppearance()
        decorView?.setBackgroundColor(color)
    }

    /** कभी भी शक हो कि state stale है — सस्ती, सुरक्षित call। */
    fun reapply() {
        gestureNav = isGestureNavigation()
        applyNavBarAppearance()
        requestInsets()
    }

    // ───────────────────────────── core logic ─────────────────────────────

    private fun applyInsets(insets: WindowInsetsCompat) {
        val view = targetView ?: return

        // ── stable insets = Gboard की "decor_view_stable_inset_bottom" ────────────
        // ignoringVisibility इसलिए, क्योंकि "stable" का मतलब ही है: transient hide/show से
        // value न बदले (immersive apps में bar छिपा हो तब भी frame मिले)।
        val nav = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.navigationBars())
        val stableBottom = nav.bottom

        // fallback — कुछ OEM/devices पर navBar.bottom 0 आता है पर gesture area non-zero होता है
        val gestureBottom = insets
            .getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemGestures()).bottom

        val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
        val caption = insets.getInsets(WindowInsetsCompat.Type.captionBar())

        // ── DecorView ने insets पहले ही consume कर लिए? (double-padding बचाव) ─────
        // API ≤ 34 पर अगर किसी device/OEM पर decorFitsSystemWindows असर कर रहा हो, तो
        // "auto" (visible) insets 0 आते हैं जबकि stable frame non-zero होता है।
        val autoBottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
        val decorAlreadyFits = stableBottom > 0 && autoBottom == 0

        gestureNav = isGestureNavigation()
        val docked = isKeyboardDockedAtBottom()
        val landscape = ime.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val floor = if (landscape) (minGapPxLandscape ?: minGapPx) else minGapPx
        val cap = (if (landscape) (maxGapPxLandscape ?: maxGapPx) else maxGapPx).takeIf { it > 0 }

        val bottom = when {
            Build.VERSION.SDK_INT < API_GESTURE_NAV -> 0     // Android 10 से पहले gesture nav नहीं
            !docked -> 0                                     // floating / split / one-handed (Gboard: normal_mode only)

            // ★ GESTURE NAV — Gboard का "keyboard_bottom_gap": height पूरी तरह device से
            gestureNav -> clamp(
                max(
                    if (stableBottom > 0) stableBottom else gestureBottom,  // primary = stable inset
                    cutout.bottom,                                          // Gboard: "Ignore display cutout area"
                    floor,
                ),
                cap,
            )

            // ★ 3-BUTTON / 2-BUTTON — कोई *visible* extra padding नहीं।
            // अगर DecorView पहले से fit कर रहा है → 0 (बिल्कुल Gboard जैसा)।
            // वरना (API 35+ enforced edge-to-edge, या हमने decorFits=false किया) → खुद लगाओ,
            // नहीं तो keys opaque nav bar के नीचे दब जाएँगी। यह padding user को दिखती नहीं।
            decorAlreadyFits -> 0

            else -> clamp(max(stableBottom, cutout.bottom, caption.bottom, floor), cap)
        }

        // landscape + 3-button में nav bar left/right में चला जाता है
        val sides = if (docked && !gestureNav && !decorAlreadyFits) nav else Insets.NONE
        val left = clamp(sides.left, cap)
        val right = clamp(sides.right, cap)

        if (bottom == lastBottom && left == lastLeft && right == lastRight) return
        lastBottom = bottom; lastLeft = left; lastRight = right

        view.setPadding(basePadLeft + left, basePadTop, basePadRight + right, basePadBottom + bottom)

        if (debugLog) {
            Log.d(
                TAG,
                "gestureNav=$gestureNav docked=$docked landscape=$landscape " +
                    "stableBottom=$stableBottom gestureBottom=$gestureBottom autoBottom=$autoBottom " +
                    "decorAlreadyFits=$decorAlreadyFits cutout=${cutout.bottom} " +
                    "→ pad(b=$bottom,l=$left,r=$right)",
            )
        }
        onPaddingChanged?.invoke(bottom, left, right)
    }

    private fun clamp(value: Int, cap: Int?): Int =
        if (cap != null && cap > 0) min(value, cap) else value

    /**
     * Padding strip को keyboard के background से match कराता है — continuous bottom bar
     * जैसा दिखे, कोई अलग color/border नहीं।
     */
    private fun applyNavBarAppearance() {
        val window = imeWindow ?: return
        val decor = window.decorView

        // strip का असली रंग यही तय करता है (padding वाली खाली जगह DecorView का background दिखाती है)
        decor.setBackgroundColor(keyboardBackgroundColor)

        runCatching {
            if (savedNavBarColor == null) savedNavBarColor = window.navigationBarColor
            // transparent → pill के पीछे DecorView का (keyboard जैसा) background दिखेगा
            window.navigationBarColor = Color.TRANSPARENT
        }
        // कुछ OEM transparent मानते नहीं → fallback में सीधे वही color दे दो:
        // runCatching { window.navigationBarColor = keyboardBackgroundColor }

        // pill contrast: light keyboard → dark pill, dark keyboard → light pill
        runCatching {
            WindowCompat.getInsetsController(window, decor)
                .isAppearanceLightNavigationBars = isLightColor(keyboardBackgroundColor)
        }
    }

    private fun restoreNavBarColor() {
        val window = imeWindow ?: return
        savedNavBarColor?.let { runCatching { window.navigationBarColor = it } }
        savedNavBarColor = null
    }

    private fun makeImeWindowEdgeToEdge(window: Window) {
        runCatching {
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowCompat.setDecorFitsSystemWindows(window, false)
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            }
        }
    }

    private fun requestInsets() {
        // post करके, वरना layout pass के दौरान recursion/ignored dispatch हो सकता है
        decorView?.post { decorView?.let { ViewCompat.requestApplyInsets(it) } }
    }

    private fun registerNavModeObserver() {
        runCatching {
            val uri = Settings.Secure.getUriFor(SETTINGS_NAVIGATION_MODE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ime.contentResolver.registerContentObserver(
                    uri, false, navModeObserver, UserHandle.USER_CURRENT,
                )
            } else {
                ime.contentResolver.registerContentObserver(uri, false, navModeObserver)
            }
        }
    }

    // ───────────────────────── mode detection ─────────────────────────

    /**
     * Android का असली system config पढ़ता है (वही जो SystemUI use करता है):
     * 0 = 3-button, 1 = 2-button, 2 = gesture.
     *
     * ❌ insets से mode detect मत करो (`systemGestures.left > 0` वाला पुराना trick):
     *    Android 15+ में 3-button mode में भी systemGestures non-zero आता है।
     *
     * नोट: Gboard खुद यह detection करता ही नहीं (APK में कोई key नहीं मिली) — वह सीधे
     * stable inset उठा लेता है। हम इसलिए करते हैं क्योंकि Android 15 पर यह ज़रूरी है और
     * Mgboard की requirement ही है कि 3-button में feature active न हो।
     */
    fun isGestureNavigation(): Boolean = getNavInteractionMode(ime) == NAV_MODE_GESTURE

    companion object {
        private const val TAG = "MgboardGestureNav"

        /** Android 10 से पहले gesture navigation मौजूद ही नहीं। */
        const val API_GESTURE_NAV = Build.VERSION_CODES.Q

        const val NAV_MODE_THREE_BUTTON = 0
        const val NAV_MODE_TWO_BUTTON = 1
        const val NAV_MODE_GESTURE = 2

        /** Undocumented पर widely-supported — सिर्फ़ observer trigger के लिए। */
        const val SETTINGS_NAVIGATION_MODE = "navigation_mode"

        @JvmStatic
        fun getNavInteractionMode(context: Context): Int {
            if (Build.VERSION.SDK_INT < API_GESTURE_NAV) return NAV_MODE_THREE_BUTTON
            val res = context.resources
            val id = res.getIdentifier("config_navBarInteractionMode", "integer", "android")
            if (id != 0) runCatching { return res.getInteger(id) }
            return runCatching {
                Settings.Secure.getInt(context.contentResolver, SETTINGS_NAVIGATION_MODE, 0)
            }.getOrDefault(NAV_MODE_THREE_BUTTON)
        }

        private fun isLightColor(@ColorInt color: Int): Boolean {
            fun lin(v: Float) =
                if (v <= 0.03928f) v / 12.92f
                else Math.pow(((v + 0.055) / 1.055).toDouble(), 2.4).toFloat()
            val l = 0.2126f * lin(Color.red(color) / 255f) +
                0.7152f * lin(Color.green(color) / 255f) +
                0.0722f * lin(Color.blue(color) / 255f)
            return l > 0.5f
        }
    }
}
