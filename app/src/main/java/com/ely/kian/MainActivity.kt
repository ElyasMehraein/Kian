package com.ely.kian

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.ely.kian.ui.navigation.KianScaffold
import com.ely.kian.ui.theme.KianTheme
import com.ely.kian.crypto.SecureStorage

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        val secureStorage = SecureStorage(newBase)
        val lang = secureStorage.getLanguage()
        super.attachBaseContext(LocaleUtils.setLocale(newBase, lang))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KianTheme {
                KianScaffold()
            }
        }
    }
}
