package app.ytfilter.ui

import android.app.Application
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.ytfilter.YtFilterApp
import app.ytfilter.core.BlockReason
import app.ytfilter.core.Decision
import app.ytfilter.core.FaceMatcher
import app.ytfilter.core.FilterPipeline
import app.ytfilter.core.KeywordFilter
import app.ytfilter.core.VideoItem
import app.ytfilter.data.ApiException
import app.ytfilter.data.Rules
import app.ytfilter.data.StoredPerson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

enum class FeedSource(val label: String) { Popular("熱門"), Subscriptions("訂閱"), Search("搜尋") }

data class FeedState(
    val source: FeedSource = FeedSource.Popular,
    val query: String = "",
    val loading: Boolean = false,
    val shown: List<VideoItem> = emptyList(),
    val blocked: List<Pair<VideoItem, BlockReason>> = emptyList(),
    val error: String? = null,
    val signedIn: Boolean = false,
)

class FeedViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as YtFilterApp

    private val _state = MutableStateFlow(FeedState())
    val state: StateFlow<FeedState> = _state.asStateFlow()

    val rules: StateFlow<Rules> = app.rules.rules.stateIn(viewModelScope, SharingStarted.Eagerly, Rules())

    val faceModelAvailable: Boolean get() = app.faceEmbedder.isAvailable

    private var accessToken: String? = null

    /** Called by the activity once Google authorization succeeds. */
    fun signedIn(token: String) {
        accessToken = token
        _state.update { it.copy(signedIn = true) }
        select(FeedSource.Subscriptions)
    }

    private var fetched: List<VideoItem> = emptyList()

    fun select(source: FeedSource) {
        _state.update { it.copy(source = source) }
        if (source != FeedSource.Search) refresh()
    }

    fun search(query: String) {
        _state.update { it.copy(source = FeedSource.Search, query = query) }
        refresh()
    }

    fun refresh() = viewModelScope.launch {
        _state.update { it.copy(loading = true, error = null) }
        try {
            val s = _state.value
            val videos = when (s.source) {
                FeedSource.Popular -> app.api.mostPopular()
                FeedSource.Subscriptions -> app.api.subscriptionFeed(
                    accessToken ?: error("請先登入 Google 帳號才能看訂閱"),
                )
                FeedSource.Search -> if (s.query.isBlank()) emptyList() else app.api.search(s.query)
            }
            fetched = videos
            applyFilters()
        } catch (e: ApiException) {
            if (e.code == 401) {
                accessToken = null // tokens last about an hour
                _state.update { it.copy(loading = false, signedIn = false, error = "登入已過期，請按右上角重新登入") }
            } else {
                _state.update { it.copy(loading = false, error = e.message) }
            }
        } catch (e: Exception) {
            _state.update { it.copy(loading = false, error = e.message) }
        }
    }

    /** Re-runs the filters on the already-fetched list; costs no API quota. */
    private suspend fun applyFilters() {
        val videos = fetched
        val decisions = evaluate(videos, app.rules.current())
        _state.update {
            it.copy(
                loading = false,
                shown = videos.filterIndexed { i, _ -> decisions[i] is Decision.Allow },
                blocked = videos.mapIndexedNotNull { i, v -> (decisions[i] as? Decision.Block)?.let { b -> v to b.reason } },
            )
        }
    }

    private suspend fun evaluate(videos: List<VideoItem>, rules: Rules): List<Decision> {
        val pipeline = FilterPipeline(
            keywords = KeywordFilter(rules.keywords),
            blockedChannelIds = rules.channels,
            faces = FaceMatcher(rules.people.map { it.toBlockedPerson() }, rules.faceThreshold),
            faceSource = app.faceSource.takeIf { app.faceEmbedder.isAvailable },
        )
        val limit = Semaphore(4) // a few thumbnails at a time keeps the UI smooth
        return withContext(Dispatchers.Default) {
            videos.map { v -> async { limit.withPermit { pipeline.evaluate(v) } } }.awaitAll()
        }
    }

    // ---- rule editing ----

    /** Returns false (and stores nothing) for an invalid `re:` pattern. */
    fun addKeyword(k: String): Boolean {
        val rule = k.trim()
        if (!KeywordFilter.isValid(rule)) return false
        editRules { r -> if (rule.isEmpty() || rule in r.keywords) r else r.copy(keywords = r.keywords + rule) }
        return true
    }
    fun removeKeyword(k: String) = editRules { it.copy(keywords = it.keywords - k) }
    fun blockChannel(v: VideoItem) = editRules { it.copy(channels = it.channels + (v.channelId to v.channelTitle)) }
    fun unblockChannel(id: String) = editRules { it.copy(channels = it.channels - id) }
    fun removePerson(name: String) = editRules { r -> r.copy(people = r.people.filterNot { it.name == name }) }
    fun setThreshold(t: Float) = editRules { it.copy(faceThreshold = t) }

    /** Embeds the largest face of each reference photo and stores it under [name]. Returns faces found. */
    suspend fun addPerson(name: String, photos: List<Uri>): Int {
        val resolver = getApplication<Application>().contentResolver
        val refs = photos.mapNotNull { uri ->
            val bitmap = withContext(Dispatchers.IO) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { d, _, _ ->
                    d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE // ML Kit and cropping need a CPU bitmap
                }
            }
            app.faceEmbedder.embedFaces(bitmap).firstOrNull()?.toList()
        }
        if (refs.isNotEmpty()) {
            app.rules.update { r ->
                val others = r.people.filterNot { it.name == name }
                val existing = r.people.find { it.name == name }?.references.orEmpty()
                r.copy(people = others + StoredPerson(name, existing + refs))
            }
            applyFilters()
        }
        return refs.size
    }

    private fun editRules(transform: (Rules) -> Rules) = viewModelScope.launch {
        app.rules.update(transform)
        applyFilters()
    }
}
