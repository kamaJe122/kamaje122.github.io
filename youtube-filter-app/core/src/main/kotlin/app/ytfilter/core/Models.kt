package app.ytfilter.core

/** One video card as returned by the YouTube Data API, reduced to what the filters need. */
data class VideoItem(
    val id: String,
    val title: String,
    val channelId: String,
    val channelTitle: String,
    val description: String = "",
    val tags: List<String> = emptyList(),
    val publishedAt: String = "", // ISO-8601, sorts lexically
    val thumbnailUrl: String = "https://i.ytimg.com/vi/$id/hqdefault.jpg",
)

/** Why a video was hidden. Shown to the user so every block is explainable. */
sealed interface BlockReason {
    data class Keyword(val rule: String, val field: String) : BlockReason
    data class Channel(val channelTitle: String) : BlockReason
    data class Face(val personName: String, val similarity: Float) : BlockReason
}

sealed interface Decision {
    data object Allow : Decision
    data class Block(val reason: BlockReason) : Decision
}
