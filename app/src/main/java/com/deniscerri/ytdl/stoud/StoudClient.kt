package com.deniscerri.ytdl.stoud

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

data class StoudQueueItem(
    val url: String,
    val title: String
)

data class StoudCurrentVideo(
    val title: String?,
    val url: String?
)

data class StoudStatus(
    val isStreaming: Boolean,
    val statusText: String,
    val currentVideo: StoudCurrentVideo?,
    val queue: List<StoudQueueItem>,
    val loopCurrentVideo: Boolean,
    val breakMode: Boolean
)

data class StoudResult(
    val success: Boolean,
    val message: String
)

class StoudClient(private val context: Context) {

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private fun normalizeBaseUrl(url: String): String {
        var trimmed = url.trim()
        if (trimmed.isEmpty()) return ""
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            trimmed = "http://$trimmed"
        }
        return trimmed.removeSuffix("/")
    }

    private fun buildRequest(endpoint: String, method: String = "GET", bodyJson: String? = null): Request {
        val baseUrl = normalizeBaseUrl(StoudPreferences.getServerUrl(context))
        if (baseUrl.isEmpty()) {
            throw IOException("Stoud server URL is not configured")
        }

        val requestUrl = "$baseUrl$endpoint"
        val builder = Request.Builder().url(requestUrl)

        val apiToken = StoudPreferences.getApiToken(context)
        if (apiToken.isNotEmpty()) {
            builder.addHeader("Authorization", "Bearer $apiToken")
            builder.addHeader("X-API-Key", apiToken)
        }

        when (method.uppercase()) {
            "GET" -> builder.get()
            "POST" -> {
                val body = (bodyJson ?: "{}").toRequestBody(jsonMediaType)
                builder.post(body)
            }
            "DELETE" -> {
                if (bodyJson != null) {
                    builder.delete(bodyJson.toRequestBody(jsonMediaType))
                } else {
                    builder.delete()
                }
            }
            else -> builder.method(method, bodyJson?.toRequestBody(jsonMediaType))
        }

        return builder.build()
    }

    suspend fun getStatus(): Result<StoudStatus> = withContext(Dispatchers.IO) {
        runCatching {
            val request = buildRequest("/api/status", "GET")
            httpClient.newCall(request).execute().use { response ->
                val bodyString = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}: $bodyString")
                }

                val json = JsonParser.parseString(bodyString).asJsonObject
                val statusStr = json.get("status")?.asString ?: "idle"
                val isStreaming = statusStr.equals("streaming", ignoreCase = true)

                var currentVideo: StoudCurrentVideo? = null
                if (json.has("current_video") && !json.get("current_video").isJsonNull) {
                    val cv = json.getAsJsonObject("current_video")
                    currentVideo = StoudCurrentVideo(
                        title = cv.get("title")?.asString,
                        url = cv.get("url")?.asString
                    )
                }

                val queueList = mutableListOf<StoudQueueItem>()
                if (json.has("queue") && json.get("queue").isJsonArray) {
                    val array = json.getAsJsonArray("queue")
                    for (elem in array) {
                        if (elem.isJsonObject) {
                            val obj = elem.asJsonObject
                            val u = obj.get("url")?.asString ?: ""
                            val t = obj.get("title")?.asString ?: "Queue Item"
                            queueList.add(StoudQueueItem(u, t))
                        }
                    }
                }

                val loop = json.get("loop_current_video")?.asBoolean ?: false
                val breakMode = json.get("break_mode")?.asBoolean ?: false

                StoudStatus(
                    isStreaming = isStreaming,
                    statusText = statusStr,
                    currentVideo = currentVideo,
                    queue = queueList,
                    loopCurrentVideo = loop,
                    breakMode = breakMode
                )
            }
        }
    }

    suspend fun startStream(
        url: String,
        quality: String? = null,
        customRtmp: String? = null
    ): StoudResult = withContext(Dispatchers.IO) {
        runCatching {
            val q = quality ?: StoudPreferences.getQuality(context)
            val rtmp = customRtmp ?: StoudPreferences.getCustomRtmp(context)

            val payload = JsonObject().apply {
                addProperty("url", url)
                addProperty("quality", q)
                if (rtmp.isNotEmpty()) {
                    addProperty("rtmp_url", rtmp)
                }
            }

            val request = buildRequest("/api/stream/start", "POST", gson.toJson(payload))
            httpClient.newCall(request).execute().use { response ->
                val bodyString = response.body?.string().orEmpty()
                val json = runCatching { JsonParser.parseString(bodyString).asJsonObject }.getOrNull()
                val success = json?.get("success")?.asBoolean ?: response.isSuccessful
                val errorMsg = json?.get("error")?.asString ?: (if (!response.isSuccessful) "HTTP ${response.code}" else "Stream started")
                StoudResult(success, if (success) "Broadcast started on Stoud!" else errorMsg)
            }
        }.getOrElse { e ->
            StoudResult(false, e.localizedMessage ?: "Network request failed")
        }
    }

    suspend fun addToQueue(url: String, title: String): StoudResult = withContext(Dispatchers.IO) {
        runCatching {
            val payload = JsonObject().apply {
                addProperty("url", url)
                addProperty("title", title)
            }

            val request = buildRequest("/api/queue/add", "POST", gson.toJson(payload))
            httpClient.newCall(request).execute().use { response ->
                val bodyString = response.body?.string().orEmpty()
                val json = runCatching { JsonParser.parseString(bodyString).asJsonObject }.getOrNull()
                val success = json?.get("success")?.asBoolean ?: response.isSuccessful
                val errorMsg = json?.get("error")?.asString ?: (if (!response.isSuccessful) "HTTP ${response.code}" else "Added to queue")
                StoudResult(success, if (success) "Added to Stoud broadcast queue!" else errorMsg)
            }
        }.getOrElse { e ->
            StoudResult(false, e.localizedMessage ?: "Failed to add to queue")
        }
    }

    suspend fun stopStream(): StoudResult = withContext(Dispatchers.IO) {
        runCatching {
            val request = buildRequest("/api/stream/stop", "POST", "{}")
            httpClient.newCall(request).execute().use { response ->
                val bodyString = response.body?.string().orEmpty()
                val json = runCatching { JsonParser.parseString(bodyString).asJsonObject }.getOrNull()
                val success = json?.get("success")?.asBoolean ?: response.isSuccessful
                StoudResult(success, if (success) "Live stream stopped" else "Failed to stop stream")
            }
        }.getOrElse { e ->
            StoudResult(false, e.localizedMessage ?: "Failed to stop stream")
        }
    }

    suspend fun clearQueue(): StoudResult = withContext(Dispatchers.IO) {
        runCatching {
            val request = buildRequest("/api/queue/clear", "POST", "{}")
            httpClient.newCall(request).execute().use { response ->
                val bodyString = response.body?.string().orEmpty()
                val json = runCatching { JsonParser.parseString(bodyString).asJsonObject }.getOrNull()
                val success = json?.get("success")?.asBoolean ?: response.isSuccessful
                StoudResult(success, if (success) "Broadcast queue cleared" else "Failed to clear queue")
            }
        }.getOrElse { e ->
            StoudResult(false, e.localizedMessage ?: "Failed to clear queue")
        }
    }

    suspend fun removeFromQueue(index: Int): StoudResult = withContext(Dispatchers.IO) {
        runCatching {
            val request = buildRequest("/api/queue/$index", "DELETE")
            httpClient.newCall(request).execute().use { response ->
                val bodyString = response.body?.string().orEmpty()
                val json = runCatching { JsonParser.parseString(bodyString).asJsonObject }.getOrNull()
                val success = json?.get("success")?.asBoolean ?: response.isSuccessful
                StoudResult(success, if (success) "Item removed from queue" else "Failed to remove item")
            }
        }.getOrElse { e ->
            StoudResult(false, e.localizedMessage ?: "Failed to remove item")
        }
    }
}
