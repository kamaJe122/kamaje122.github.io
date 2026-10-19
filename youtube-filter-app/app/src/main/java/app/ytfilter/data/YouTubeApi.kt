package app.ytfilter.data

import app.ytfilter.core.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

class ApiException(val code: Int, message: String) : Exception(message)

/**
 * Minimal YouTube Data API v3 client. Quota (10,000 units/day by default):
 * videos.list / playlistItems.list / subscriptions.list = 1 unit, search.list = 100 units.
 */
class YouTubeApi(private val http: OkHttpClient, private val apiKey: String) {
    private val json = Json { ignoreUnknownKeys = true }
    private val base = "https://www.googleapis.com/youtube/v3/".toHttpUrl()

    suspend fun mostPopular(regionCode: String = "TW"): List<VideoItem> =
        get("videos", null, "part" to "snippet", "chart" to "mostPopular", "regionCode" to regionCode, "maxResults" to "50")
            .items().map(::toVideo)

    suspend fun search(query: String): List<VideoItem> {
        val ids = get("search", null, "part" to "id", "type" to "video", "q" to query, "maxResults" to "25")
            .items().mapNotNull { it["id"]?.jsonObject?.get("videoId")?.jsonPrimitive?.content }
        return videos(ids, null)
    }

    /** Latest uploads from the signed-in user's subscriptions (requires youtube.readonly OAuth token). */
    suspend fun subscriptionFeed(token: String, perChannel: Int = 3): List<VideoItem> {
        val channelIds = get("subscriptions", token, "part" to "snippet", "mine" to "true", "maxResults" to "50")
            .items().mapNotNull {
                it["snippet"]?.jsonObject?.get("resourceId")?.jsonObject?.get("channelId")?.jsonPrimitive?.content
            }
        val videoIds = channelIds.flatMap { channelId ->
            // Every channel's uploads playlist is its channel id with "UC" replaced by "UU".
            val uploads = "UU" + channelId.removePrefix("UC")
            runCatching {
                get("playlistItems", token, "part" to "contentDetails", "playlistId" to uploads, "maxResults" to "$perChannel")
                    .items().mapNotNull { it["contentDetails"]?.jsonObject?.get("videoId")?.jsonPrimitive?.content }
            }.getOrDefault(emptyList())
        }
        return videos(videoIds, token).sortedByDescending { it.publishedAt }
    }

    private suspend fun videos(ids: List<String>, token: String?): List<VideoItem> =
        ids.distinct().chunked(50).flatMap { chunk ->
            get("videos", token, "part" to "snippet", "id" to chunk.joinToString(",")).items().map(::toVideo)
        }

    private fun toVideo(item: JsonObject): VideoItem {
        val s = item["snippet"]!!.jsonObject
        fun str(key: String) = s[key]?.jsonPrimitive?.content.orEmpty()
        val id = item["id"]!!.jsonPrimitive.content
        val thumbs = s["thumbnails"]?.jsonObject
        val thumb = listOf("high", "medium", "default").firstNotNullOfOrNull {
            thumbs?.get(it)?.jsonObject?.get("url")?.jsonPrimitive?.content
        }
        return VideoItem(
            id = id,
            title = str("title"),
            channelId = str("channelId"),
            channelTitle = str("channelTitle"),
            description = str("description"),
            tags = s["tags"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty(),
            publishedAt = str("publishedAt"),
            thumbnailUrl = thumb ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg",
        )
    }

    private fun JsonObject.items() = this["items"]?.jsonArray?.map { it.jsonObject }.orEmpty()

    private suspend fun get(path: String, token: String?, vararg params: Pair<String, String>): JsonObject =
        withContext(Dispatchers.IO) {
            val url: HttpUrl = base.newBuilder().addPathSegment(path).apply {
                params.forEach { (k, v) -> addQueryParameter(k, v) }
                if (token == null) addQueryParameter("key", apiKey)
            }.build()
            val request = Request.Builder().url(url).apply {
                if (token != null) header("Authorization", "Bearer $token")
            }.build()
            http.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw ApiException(resp.code, "YouTube API $path: HTTP ${resp.code} $body")
                json.parseToJsonElement(body).jsonObject
            }
        }
}
