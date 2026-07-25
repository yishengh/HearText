package com.yishenghuang.heartext

import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.yishenghuang.heartext.data.AppLanguage
import com.yishenghuang.heartext.ui.HearTextRoot
import com.yishenghuang.heartext.ui.theme.HearTextTheme
import com.yishenghuang.heartext.util.LocaleHelper

class MainActivity : AppCompatActivity() {
    /** Returns true if the key event was handled (e.g. volume page-turn). */
    var keyEventInterceptor: ((KeyEvent) -> Boolean)? = null

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val raw = prefs.getString("app_language", AppLanguage.SYSTEM.name) ?: AppLanguage.SYSTEM.name
        val language = runCatching { AppLanguage.valueOf(raw) }.getOrDefault(AppLanguage.SYSTEM)
        super.attachBaseContext(LocaleHelper.wrap(newBase, language))
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (keyEventInterceptor?.invoke(event) == true) return true
        return super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HearTextTheme(darkTheme = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFFFBFBFC)
                ) {
                    HearTextRoot()
                }
            }
        }
    }
}
