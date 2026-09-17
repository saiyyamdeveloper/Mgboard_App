package com.mgboard.keyboard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.mgboard.keyboard.prefs.SgPrefs
import com.mgboard.keyboard.ui.MgTheme
import com.mgboard.keyboard.ui.SettingsScreen

/** Gboard-style multi-page Settings (14 pages · 90 items · search). */
class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = SgPrefs(this)
        setContent {
            MgTheme { SettingsScreen(prefs = prefs, onExit = { finish() }) }
        }
    }
}
