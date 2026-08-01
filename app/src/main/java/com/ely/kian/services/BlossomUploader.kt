package com.ely.kian.services

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.ely.kian.crypto.KianKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

object BlossomUploader {
    private const val DEFAULT_SERVER = "https://blossom.primal.net/upload"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun uploadImage(
        context: Context,
        uri: Uri,
        privKey: ByteArray,
        pubKey: String,
        serverUrl: String = DEFAULT_SERVER
    ): String = withContext(Dispatchers.IO) {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw Exception("Could not open image")
            
        val bytes = inputStream.readBytes()
        inputStream.close()
        
        // Calculate SHA-256 for the payload
        val sha256 = MessageDigest.getInstance("SHA-256").digest(bytes)
        val payloadHash = KianKeys.bytesToHex(sha256)
        
        // Construct Kind 24242 event (Blossom BUD-11 Auth)
        val createdAt = System.currentTimeMillis() / 1000
        val expiration = createdAt + 3600 // 1 hour expiration
        val kind = 24242
        val tags = listOf(
            listOf("t", "upload"),
            listOf("x", payloadHash),
            listOf("expiration", expiration.toString())
        )
        val content = "Upload Blob"
        
        val id = KianKeys.computeEventId(pubkey = pubKey, createdAt = createdAt, kind = kind, tags = tags, content = content)
        val sig = KianKeys.bytesToHex(KianKeys.sign(KianKeys.hexToBytes(id), privKey))
        
        // Note: Blossom BUD-11 auth expects the full event
        val authEventJson = JSONObject().apply {
            put("id", id)
            put("pubkey", pubKey)
            put("created_at", createdAt)
            put("kind", kind)
            
            val tagsArray = org.json.JSONArray()
            tags.forEach { tagList ->
                val tagArray = org.json.JSONArray()
                tagList.forEach { tagArray.put(it) }
                tagsArray.put(tagArray)
            }
            put("tags", tagsArray)
            put("content", content)
            put("sig", sig)
        }
        
        val base64Auth = Base64.encodeToString(
            authEventJson.toString().toByteArray(Charsets.UTF_8), 
            Base64.NO_WRAP
        )
        val authHeader = "Nostr $base64Auth"
        
        val mediaType = context.contentResolver.getType(uri)?.toMediaTypeOrNull() ?: "application/octet-stream".toMediaTypeOrNull()
        val requestBody = bytes.toRequestBody(mediaType)
        
        val request = Request.Builder()
            .url(serverUrl)
            .put(requestBody)
            .addHeader("Authorization", authHeader)
            .addHeader("X-SHA-256", payloadHash)
            .build()
            
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            val errBody = response.body?.string()
            throw Exception("Upload failed: ${response.code} $errBody")
        }
        
        val responseBody = response.body?.string() ?: throw Exception("Empty response from server")
        val json = JSONObject(responseBody)
        if (json.has("url")) {
            return@withContext json.getString("url")
        } else {
            throw Exception("URL not found in response: $responseBody")
        }
    }
}
