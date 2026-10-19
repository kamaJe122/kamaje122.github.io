package app.ytfilter.core

/** Detects faces in a thumbnail and returns one embedding per face. Implemented on Android with ML Kit + LiteRT. */
fun interface FaceSource {
    suspend fun embeddings(video: VideoItem): List<FloatArray>
}

/**
 * Cheap checks first: keywords → channel blocklist → AI face check.
 * The face model only runs for videos that survive the text checks.
 */
class FilterPipeline(
    private val keywords: KeywordFilter,
    private val blockedChannelIds: Map<String, String>, // channelId -> display name
    private val faces: FaceMatcher,
    private val faceSource: FaceSource?,
) {
    suspend fun evaluate(video: VideoItem): Decision {
        keywords.check(video)?.let { return Decision.Block(it) }
        blockedChannelIds[video.channelId]?.let { return Decision.Block(BlockReason.Channel(it)) }
        val source = faceSource
        if (source != null && !faces.isEmpty) {
            val found = runCatching { source.embeddings(video) }.getOrDefault(emptyList())
            faces.check(found)?.let { return Decision.Block(it) }
        }
        return Decision.Allow
    }
}
