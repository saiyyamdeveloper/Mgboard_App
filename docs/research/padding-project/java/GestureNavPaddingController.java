package com.mgboard.ime.insets;

import android.content.Context;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.graphics.Color;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/**
 * Mgboard — Gesture-navigation bottom safe-area padding (**Gboard-exact**, APK से verified), Java version.
 *
 * behaviour:
 *  - gesture navigation ON  → last key-row के नीचे system की gap जितनी extra padding,
 *                             keyboard के background color में (continuous strip, कोई border नहीं)
 *  - 3-button navigation ON → कोई visible extra padding नहीं
 *  - height हमेशा WindowInsets से (कोई hardcoded value नहीं; floor भी default 0)
 *  - live mode switch पर layout खुद adjust (IME restart नहीं)
 *
 * ── Gboard APK (18.3.1-beta) से मिली असली internal keys ─────────────────────────
 *   normal_mode_keyboard_bottom_gap_portrait / _landscape
 *   normal_mode_decor_view_stable_inset_bottom_portrait / _landscape
 * → (a) gap सिर्फ़ "normal mode" (floating/split/one-handed में नहीं)
 *   (b) height = DecorView का **stable bottom inset**  → getInsetsIgnoringVisibility(navigationBars())
 *   (c) portrait/landscape अलग-अलग
 *
 * देखो: padding-project/GBOARD-VERIFICATION.md (पूरा तुलना-रिपोर्ट)
 *       padding-project/RESEARCH.md        (AOSP evidence के साथ विश्लेषण)
 */
public final class GestureNavPaddingController {

    private static final String TAG = "MgboardGestureNav";

    /** Android 10 (Q) से पहले gesture navigation मौजूद ही नहीं। */
    public static final int API_GESTURE_NAV = Build.VERSION_CODES.Q;

    public static final int NAV_MODE_THREE_BUTTON = 0;
    public static final int NAV_MODE_TWO_BUTTON = 1;
    public static final int NAV_MODE_GESTURE = 2;

    /** Undocumented पर widely-supported key — सिर्फ़ ContentObserver trigger के लिए। */
    public static final String SETTINGS_NAVIGATION_MODE = "navigation_mode";

    public interface OnPaddingChangedListener {
        void onPaddingChanged(int bottomPx, int leftPx, int rightPx);
    }

    /** floating / split / one-handed mode में false लौटाओ → gap 0 हो जाएगी (Gboard: normal_mode only)। */
    public interface DockedCheck {
        boolean isKeyboardDockedAtBottom();
    }

    private final InputMethodService ime;
    @ColorInt private int keyboardBackgroundColor;

    @Nullable private View targetView;
    @Nullable private View decorView;
    @Nullable private Window imeWindow;

    private int basePadLeft, basePadTop, basePadRight, basePadBottom;
    private int lastBottom = -1, lastLeft = -1, lastRight = -1;

    private boolean gestureNav;
    @Nullable private Integer savedNavBarColor;

    /** **Default 0 = Gboard-exact** — Gboard device inset में अपना कुछ नहीं जोड़ता। */
    private int minGapPx = 0;
    /** Safety cap; 0 = cap बंद (यह layout choice नहीं, सिर्फ़ garbage-value guard है)। */
    private int maxGapPx = 0;
    /** landscape के लिए अलग values (Gboard: *_bottom_gap_landscape); null = वही जो portrait के हैं। */
    @Nullable private Integer minGapPxLandscape;
    @Nullable private Integer maxGapPxLandscape;

    private boolean debugLog = false;

    @Nullable private OnPaddingChangedListener onPaddingChanged;
    @NonNull private DockedCheck dockedCheck = () -> true;

    private final Handler handler = new Handler(Looper.getMainLooper());

    /** keyboard छिपी हो तब भी mode switch पकड़ने के लिए। */
    private final ContentObserver navModeObserver = new ContentObserver(handler) {
        @Override public void onChange(boolean selfChange) {
            if (debugLog) Log.d(TAG, "navigation_mode changed -> reapply");
            reapply();
        }
    };

    public GestureNavPaddingController(@NonNull InputMethodService ime,
                                       @ColorInt int keyboardBackgroundColor) {
        this.ime = ime;
        this.keyboardBackgroundColor = keyboardBackgroundColor;
    }

    // ───────────────────────── setters ─────────────────────────

    public void setDockedCheck(@Nullable DockedCheck check) {
        this.dockedCheck = (check != null) ? check : () -> true;
    }

    public void setOnPaddingChangedListener(@Nullable OnPaddingChangedListener l) {
        this.onPaddingChanged = l;
    }

    public void setDebugLog(boolean enabled) { this.debugLog = enabled; }

    /** Default 0 (Gboard-exact). कुछ OEM insets 0 देते हैं तब 8–12dp दे सकते हो। */
    public void setMinGapPx(int px) { this.minGapPx = Math.max(0, px); }

    /** 0 = cap बंद. */
    public void setMaxGapPx(int px) { this.maxGapPx = Math.max(0, px); }

    public void setMinGapPxLandscape(@Nullable Integer px) { this.minGapPxLandscape = px; }

    public void setMaxGapPxLandscape(@Nullable Integer px) { this.maxGapPxLandscape = px; }

    /** theme बदलने पर (day/night, user theme) call करो। */
    public void setKeyboardBackgroundColor(@ColorInt int color) {
        if (keyboardBackgroundColor == color) return;
        keyboardBackgroundColor = color;
        applyNavBarAppearance();
        if (decorView != null) decorView.setBackgroundColor(color);
    }

    // ───────────────────────── lifecycle ─────────────────────────

    /** MgboardImeService.onCreate() में, input view बनने के बाद call करो। */
    public void attach(@NonNull View rootView) {
        targetView = rootView;
        basePadLeft = rootView.getPaddingLeft();
        basePadTop = rootView.getPaddingTop();
        basePadRight = rootView.getPaddingRight();
        basePadBottom = rootView.getPaddingBottom();

        Window window = imeWindow();
        if (window == null) return;
        imeWindow = window;
        decorView = window.getDecorView();

        makeImeWindowEdgeToEdge(window);

        // ★ dynamic insets listener — nav mode / rotation / OEM setting बदलते ही fire होता है
        ViewCompat.setOnApplyWindowInsetsListener(window.getDecorView(),
                (v, insets) -> {
                    applyInsets(insets);
                    return insets; // CONSUMED मत लौटाओ — बच्चों तक insets जाने दो
                });

        registerNavModeObserver();
        requestInsets();
    }

    /** onWindowShown() में call करो। */
    public void onWindowShown() {
        gestureNav = isGestureNavigation();
        applyNavBarAppearance();
        requestInsets();
    }

    /** onStartInputView() में call करो। */
    public void onStartInputView() { requestInsets(); }

    /** onConfigurationChanged() में call करो — RRO overlay बदलने पर config change आता है। */
    public void onConfigurationChanged(@Nullable Configuration newConfig) {
        gestureNav = isGestureNavigation();
        applyNavBarAppearance();
        requestInsets();
    }

    /** onDestroy() में call करो। */
    public void detach() {
        try { ime.getContentResolver().unregisterContentObserver(navModeObserver); } catch (Exception ignored) { }
        if (decorView != null) ViewCompat.setOnApplyWindowInsetsListener(decorView, null);
        restoreNavBarColor();
        decorView = null;
        imeWindow = null;
        targetView = null;
        lastBottom = lastLeft = lastRight = -1;
    }

    /** कभी भी शक हो कि state stale है। */
    public void reapply() {
        gestureNav = isGestureNavigation();
        applyNavBarAppearance();
        requestInsets();
    }

    // ───────────────────────── core ─────────────────────────

    private void applyInsets(@NonNull WindowInsetsCompat insets) {
        View view = targetView;
        if (view == null) return;

        // ── stable insets = Gboard की "decor_view_stable_inset_bottom" ────────────
        // ignoringVisibility इसलिए: "stable" का मतलब ही है कि transient hide/show से value न बदले।
        Insets nav = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.navigationBars());
        int stableBottom = nav.bottom;

        // fallback — कुछ OEM/devices पर navBar.bottom 0 आता है पर gesture area non-zero होता है
        Insets gestures = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemGestures());
        int gestureBottom = gestures.bottom;

        Insets cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout());
        Insets caption = insets.getInsets(WindowInsetsCompat.Type.captionBar());

        // ── DecorView ने insets पहले ही consume कर लिए? (double-padding बचाव) ─────
        // API ≤ 34 पर अगर किसी device/OEM पर decorFitsSystemWindows असर कर रहा हो तो
        // "auto" (visible) insets 0 आते हैं जबकि stable frame non-zero होता है।
        int autoBottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
        boolean decorAlreadyFits = stableBottom > 0 && autoBottom == 0;

        gestureNav = isGestureNavigation();
        boolean docked = dockedCheck.isKeyboardDockedAtBottom();
        boolean landscape =
                ime.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        int floor = landscape && minGapPxLandscape != null ? minGapPxLandscape : minGapPx;
        int cap = landscape && maxGapPxLandscape != null ? maxGapPxLandscape : maxGapPx;

        int bottom;
        if (Build.VERSION.SDK_INT < API_GESTURE_NAV) {
            bottom = 0;                                     // gesture nav मौजूद ही नहीं
        } else if (!docked) {
            bottom = 0;                                     // floating / split / one-handed
        } else if (gestureNav) {
            // ★ GESTURE NAV — Gboard का "keyboard_bottom_gap": height पूरी तरह device से
            int gap = Math.max(
                    Math.max(stableBottom > 0 ? stableBottom : gestureBottom, cutout.bottom),
                    floor);
            bottom = clamp(gap, cap);
        } else if (decorAlreadyFits) {
            // ★ 3-BUTTON + DecorView पहले से fit कर रहा है → बिल्कुल Gboard जैसा: कोई extra padding नहीं
            bottom = 0;
        } else {
            // ★ 3-BUTTON + edge-to-edge (API 35+ / decorFits=false) → खुद लगाना ज़रूरी,
            //   वरना keys opaque nav bar के नीचे दब जाएँगी। यह padding user को दिखती नहीं।
            bottom = clamp(Math.max(Math.max(Math.max(stableBottom, cutout.bottom), caption.bottom), floor), cap);
        }

        // landscape + 3-button में nav bar left/right में चला जाता है
        Insets sides = (docked && !gestureNav && !decorAlreadyFits) ? nav : Insets.NONE;
        int left = clamp(sides.left, cap);
        int right = clamp(sides.right, cap);

        if (bottom == lastBottom && left == lastLeft && right == lastRight) return;
        lastBottom = bottom;
        lastLeft = left;
        lastRight = right;

        // हमेशा absolute set करो — `+=` कभी नहीं (listener कई बार fire होता है)
        view.setPadding(basePadLeft + left, basePadTop, basePadRight + right,
                        basePadBottom + bottom);

        if (debugLog) {
            Log.d(TAG, "gestureNav=" + gestureNav + " docked=" + docked + " landscape=" + landscape
                    + " stableBottom=" + stableBottom + " gestureBottom=" + gestureBottom
                    + " autoBottom=" + autoBottom + " decorAlreadyFits=" + decorAlreadyFits
                    + " cutout=" + cutout.bottom
                    + " -> pad(b=" + bottom + ",l=" + left + ",r=" + right + ")");
        }
        if (onPaddingChanged != null) onPaddingChanged.onPaddingChanged(bottom, left, right);
    }

    /** 0 = cap बंद। */
    private static int clamp(int value, int cap) {
        return (cap > 0) ? Math.min(value, cap) : value;
    }

    /** padding strip को keyboard के background से match कराता है (continuous bar look)। */
    private void applyNavBarAppearance() {
        Window window = imeWindow;
        if (window == null) return;
        View decor = window.getDecorView();

        decor.setBackgroundColor(keyboardBackgroundColor);

        try {
            if (savedNavBarColor == null) savedNavBarColor = window.getNavigationBarColor();
            // transparent → pill के पीछे DecorView का keyboard-color background दिखेगा
            window.setNavigationBarColor(Color.TRANSPARENT);
        } catch (Exception ignored) { }
        // कुछ OEM transparent नहीं मानते → fallback:
        // try { window.setNavigationBarColor(keyboardBackgroundColor); } catch (Exception ignored) { }

        try {
            WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, decor);
            controller.setAppearanceLightNavigationBars(isLightColor(keyboardBackgroundColor));
        } catch (Exception ignored) { }
    }

    private void restoreNavBarColor() {
        Window window = imeWindow;
        if (window == null || savedNavBarColor == null) return;
        try { window.setNavigationBarColor(savedNavBarColor); } catch (Exception ignored) { }
        savedNavBarColor = null;
    }

    /**
     * ⚠️ ज़रूरी: default IME window में decorFitsSystemWindows == true रहता है (API ≤ 34), तब
     * DecorView insets consume कर लेता है और listener को bottom ≈ 0 मिलता है।
     */
    private void makeImeWindowEdgeToEdge(@NonNull Window window) {
        try {
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowCompat.setDecorFitsSystemWindows(window, false);
            } else {
                View decor = window.getDecorView();
                //noinspection deprecation
                decor.setSystemUiVisibility(decor.getSystemUiVisibility()
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
            }
        } catch (Exception e) {
            Log.w(TAG, "edge-to-edge setup failed", e);
        }
    }

    private void requestInsets() {
        View decor = decorView;
        if (decor == null) return;
        decor.post(() -> {
            View d = decorView;
            if (d != null) ViewCompat.requestApplyInsets(d);
        });
    }

    private void registerNavModeObserver() {
        try {
            android.net.Uri uri = Settings.Secure.getUriFor(SETTINGS_NAVIGATION_MODE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ime.getContentResolver().registerContentObserver(
                        uri, false, navModeObserver, UserHandle.USER_CURRENT);
            } else {
                ime.getContentResolver().registerContentObserver(uri, false, navModeObserver);
            }
        } catch (Exception e) {
            Log.w(TAG, "nav-mode observer registration failed", e);
        }
    }

    @Nullable
    private Window imeWindow() {
        try {
            return (ime.getWindow() != null) ? ime.getWindow().getWindow() : null;
        } catch (Exception e) {
            return null;
        }
    }

    // ───────────────────── mode detection (static helpers) ─────────────────────

    public boolean isGestureNavigation() {
        return getNavInteractionMode(ime) == NAV_MODE_GESTURE;
    }

    /**
     * Android का असली system config (वही जो SystemUI पढ़ता है): 0 = 3-button, 1 = 2-button, 2 = gesture.
     *
     * ❌ insets से mode detect मत करो — Android 15+ में 3-button mode में भी systemGestures
     *    non-zero आता है, इसलिए पुराना heuristic टूट चुका है।
     *
     * नोट: Gboard खुद यह detection नहीं करता (APK में कोई key नहीं मिली) — वह सीधे stable inset
     * उठा लेता है। हम इसलिए करते हैं क्योंकि Android 15 पर ज़रूरी है और Mgboard की requirement
     * ही है कि 3-button में feature active न हो।
     */
    public static int getNavInteractionMode(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < API_GESTURE_NAV) return NAV_MODE_THREE_BUTTON;
        try {
            int id = context.getResources()
                    .getIdentifier("config_navBarInteractionMode", "integer", "android");
            if (id != 0) return context.getResources().getInteger(id);
        } catch (Exception ignored) { }
        try {
            return Settings.Secure.getInt(context.getContentResolver(),
                    SETTINGS_NAVIGATION_MODE, NAV_MODE_THREE_BUTTON);
        } catch (Exception ignored) { }
        return NAV_MODE_THREE_BUTTON;
    }

    private static boolean isLightColor(@ColorInt int color) {
        double l = 0.2126 * lin(Color.red(color) / 255f)
                 + 0.7152 * lin(Color.green(color) / 255f)
                 + 0.0722 * lin(Color.blue(color) / 255f);
        return l > 0.5;
    }

    private static double lin(float v) {
        return (v <= 0.03928f) ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }
}
