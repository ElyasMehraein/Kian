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
import androidx.compose.runtime.mutableStateOf
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

    private var initialChatRoomIdState = mutableStateOf<String?>(null)
    private var initialPubkeyState = mutableStateOf<String?>(null)

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

        initialChatRoomIdState.value = intent.getStringExtra("chat_room_id")
        initialPubkeyState.value = if (intent.action == android.content.Intent.ACTION_VIEW) {
            extractNostrId(intent.data)
        } else null

        setContent {
            KianTheme {
                KianScaffold(
                    initialChatRoomId = initialChatRoomIdState.value,
                    initialPubkey = initialPubkeyState.value
                )
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        val chatRoomId = intent.getStringExtra("chat_room_id")
        if (chatRoomId != null) {
            initialChatRoomIdState.value = chatRoomId
        }
        if (intent.action == android.content.Intent.ACTION_VIEW) {
            val pk = extractNostrId(intent.data)
            if (pk != null) {
                initialPubkeyState.value = pk
            }
        }
    }

    private fun extractNostrId(uri: android.net.Uri?): String? {
        if (uri == null) return null
        if (uri.scheme == "nostr") return uri.schemeSpecificPart
        
        val path = uri.path
        if (path != null) {
            val nostrIdRegex = "(n(pub|profile|ote|event)1[a-z0-9]+)".toRegex()
            val match = nostrIdRegex.find(path)
            if (match != null) return match.value
        }
        
        return uri.getQueryParameter("npub") ?: uri.getQueryParameter("pk")
    }
}
