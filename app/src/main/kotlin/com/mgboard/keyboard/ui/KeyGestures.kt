package com.mgboard.keyboard.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import com.mgboard.keyboard.layout.KeyKind
import com.mgboard.keyboard.layout.KeyTiming
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Key gesture behaviour — web ke `attachKey()` ka port, **exact timings ke saath**:
 *
 *  - press par haptic (agar settings mein ON hai)
 *  - `repeat` keys (backspace): 400ms hold → pehla repeat, phir har 70ms
 *  - long-press alternate/popup: 300ms
 *  - release par commit (web: "Commit alternates only on release, never after a
 *    cancelled gesture") — repeat ho chuka ho to tap fire nahi hota
 *  - period key: multi-popup + drag deadzone 14dp (yahan long-press popup se pick)
 */
object KeyGestures {

    /**
     * @param key         unique key identity (row/col se stable) — gesture restart ke liye
     * @param repeat      true = backspace jaisi hold-repeat key
     * @param onPress     haptic + visual press
     * @param onTap       normal tap action
     * @param onLongPress alternate/popup dikhao
     * @param onRelease   alternate commit / cleanup
     */
    fun pointerInputFor(
        key: Any?,
        repeat: Boolean,
        onPress: () -> Unit,
        onTap: () -> Unit,
        onLongPress: () -> Unit,
        onRelease: () -> Unit,
    ) = pointerInput(key) {
        detectTapGestures(
            onPress = { offset ->
                onPress()
                var repeated = false
                var longShown = false
                // hold loop alag coroutine mein; release par cancel (web ke
                // clearTimeout(holdTimer) + clearInterval(repeatTimer) jaisa)
                val scope = this
                val job = scope.launch {
                    if (repeat) {
                        delay(KeyTiming.REPEAT_INITIAL_DELAY_MS)
                        repeated = true
                        onTap()
                        while (true) {
                            delay(KeyTiming.REPEAT_INTERVAL_MS)
                            onTap()
                        }
                    } else {
                        delay(KeyTiming.LONG_PRESS_MS)
                        longShown = true
                        onLongPress()
                    }
                }
                val released = tryAwaitRelease()
                job.cancel()
                if (released) {
                    when {
                        repeated -> Unit                 // web: `if (repeated) return;`
                        longShown -> onRelease()         // alternate commit
                        else -> onTap()
                    }
                } else if (longShown) {
                    onRelease()
                }
            },
        )
    }

    fun isRepeatKind(kind: KeyKind) = kind == KeyKind.BACKSPACE
}
