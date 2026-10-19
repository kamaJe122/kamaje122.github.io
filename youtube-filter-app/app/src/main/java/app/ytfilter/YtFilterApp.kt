package app.ytfilter

import android.app.Application
import app.ytfilter.ai.FaceEmbedder
import app.ytfilter.ai.ThumbnailFaceSource
import app.ytfilter.data.RulesStore
import app.ytfilter.data.YouTubeApi
import okhttp3.OkHttpClient

/** Holds app-wide singletons; small enough that a DI framework isn't worth it. */
class YtFilterApp : Application() {
    val http by lazy { OkHttpClient() }
    val api by lazy { YouTubeApi(http, BuildConfig.YOUTUBE_API_KEY) }
    val rules by lazy { RulesStore(this) }
    val faceEmbedder by lazy { FaceEmbedder(this) }
    val faceSource by lazy { ThumbnailFaceSource(http, faceEmbedder) }
}
