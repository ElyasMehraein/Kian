package com.ely.kian.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object NavigationUtils {
    fun openInMaps(context: Context, lat: Double, lon: Double, label: String = "Location") {
        val uri = Uri.parse("geo:$lat,$lon?q=$lat,$lon($label)")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "No map application found", Toast.LENGTH_SHORT).show()
        }
    }
}
