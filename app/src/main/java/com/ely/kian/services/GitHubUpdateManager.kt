package com.ely.kian.services

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.ely.kian.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

class GitHubUpdateManager(private val context: Context) {
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    // GitHub repository details
    private val repoOwner = "ElyasMehraein"
    private val repoName = "Kian"

    data class UpdateResult(
        val isUpdateAvailable: Boolean,
        val latestVersion: String? = null,
        val downloadUrl: String? = null,
        val releaseNotes: String? = null
    )

    suspend fun checkForUpdates(): Result<UpdateResult> = withContext(Dispatchers.IO) {
        try {
            // Fetch all releases to include pre-releases (alphas/betas)
            val request = Request.Builder()
                .url("https://api.github.com/repos/$repoOwner/$repoName/releases")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext Result.failure(Exception("Failed to fetch releases: ${response.code}"))

                val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response body"))
                val releases = json.parseToJsonElement(body).jsonArray
                
                if (releases.isEmpty()) return@withContext Result.success(UpdateResult(false))

                // The first one in the list is the most recent release (including pre-releases)
                val releaseJson = releases[0].jsonObject

                val tagName = releaseJson["tag_name"]?.jsonPrimitive?.content ?: ""
                val releaseNotes = releaseJson["body"]?.jsonPrimitive?.content
                
                val assets = releaseJson["assets"]?.jsonArray
                val apkAsset = assets?.firstOrNull { 
                    it.jsonObject["name"]?.jsonPrimitive?.content?.endsWith(".apk") == true 
                }
                val downloadUrl = apkAsset?.jsonObject["browser_download_url"]?.jsonPrimitive?.content

                val currentVersion = BuildConfig.VERSION_NAME
                val isUpdateAvailable = isVersionNewer(currentVersion, tagName)

                Result.success(UpdateResult(isUpdateAvailable, tagName, downloadUrl, releaseNotes))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun isVersionNewer(current: String, latest: String): Boolean {
        val currentClean = current.removePrefix("v").split("-")[0]
        val latestClean = latest.removePrefix("v").split("-")[0]
        
        val currentParts = currentClean.split(".").mapNotNull { it.toIntOrNull() }
        val latestParts = latestClean.split(".").mapNotNull { it.toIntOrNull() }
        
        for (i in 0 until maxOf(currentParts.size, latestParts.size)) {
            val c = currentParts.getOrElse(i) { 0 }
            val l = latestParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (c > l) return false
        }
        return false
    }

    fun openDownloadUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
