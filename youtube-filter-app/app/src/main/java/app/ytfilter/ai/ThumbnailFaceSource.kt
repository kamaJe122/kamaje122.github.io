package app.ytfilter.ai

import android.graphics.BitmapFactory
import android.util.LruCache
import app.ytfilter.core.FaceSource
import app.ytfilter.core.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Downloads a video's thumbnail and returns the face embeddings in it, cached per video. */
class ThumbnailFaceSource(
    private val http: OkHttpClient,
    private val embedder: FaceEmbedder,
) : FaceSource {
    private val cache = LruCache<String, List<FloatArray>>(500)

    override suspend fun embeddings(video: VideoItem): List<FloatArray> {
        cache.get(video.id)?.let { return it }
        val bitmap = withContext(Dispatchers.IO) {
            http.newCall(Request.Builder().url(video.thumbnailUrl).build()).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                resp.body?.byteStream()?.let(BitmapFactory::decodeStream)
            }
        } ?: return emptyList()
        return embedder.embedFaces(bitmap).also { cache.put(video.id, it) }
    }
}
