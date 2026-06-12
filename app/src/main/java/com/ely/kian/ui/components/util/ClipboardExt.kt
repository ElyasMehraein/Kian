package com.ely.kian.ui.components.util

import android.content.ClipData
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard

suspend fun Clipboard.setText(text: String) {
    setClipEntry(ClipEntry(ClipData.newPlainText("", text)))
}
