package com.ely.kian

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.ely.kian.ui.navigation.KianScaffold
import com.ely.kian.ui.theme.KianTheme
import com.ely.kian.crypto.SecureStorage

class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        // Handle permission result if needed
    }

    override fun attachBaseContext(newBase: Context) {
        val secureStorage = SecureStorage(newBase)
        val lang = secureStorage.getLanguage()
        super.attachBaseContext(LocaleUtils.setLocale(newBase, lang))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val chatRoomId = intent.getStringExtra("chat_room_id")
        val initialPubkey = if (intent.action == android.content.Intent.ACTION_VIEW) {
            intent.data?.getQueryParameter("pk")
        } else null

        setContent {
            KianTheme {
                KianScaffold(
                    initialChatRoomId = chatRoomId,
                    initialPubkey = initialPubkey
                )
            }
        }
    }
}
