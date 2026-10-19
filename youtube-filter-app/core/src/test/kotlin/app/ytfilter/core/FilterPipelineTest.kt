package app.ytfilter.core

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class FilterPipelineTest {
    private val video = VideoItem(
        id = "abc",
        title = "【開箱】ＩＰＨＯＮＥ 18 實測",
        channelId = "UC1",
        channelTitle = "3C Channel",
        tags = listOf("review"),
    )

    @Test
    fun keywordMatchIsCaseAndWidthInsensitive() {
        val r = KeywordFilter(listOf("iphone")).check(video)
        assertEquals("title", r?.field)
    }

    @Test
    fun regexRule() {
        assertEquals("re:開箱.*實測", KeywordFilter(listOf("re:開箱.*實測")).check(video)?.rule)
        assertNull(KeywordFilter(listOf("re:^實測")).check(video))
    }

    @Test
    fun invalidRegexSkippedNotThrown() {
        assertEquals(false, KeywordFilter.isValid("re:("))
        assertEquals("iphone", KeywordFilter(listOf("re:(", "iphone")).check(video)?.rule)
    }

    @Test
    fun blankRulesIgnored() {
        assertNull(KeywordFilter(listOf("", "  ")).check(video))
    }

    @Test
    fun faceMatchAboveThreshold() {
        val matcher = FaceMatcher(listOf(BlockedPerson("X", listOf(floatArrayOf(1f, 0f, 0f)))))
        assertEquals("X", matcher.check(listOf(floatArrayOf(0f, 1f, 0f), floatArrayOf(2f, 0.2f, 0f)))?.personName)
        assertNull(matcher.check(listOf(floatArrayOf(0f, 1f, 0f))))
    }

    @Test
    fun faceMatchSkipsMismatchedDimensions() {
        val matcher = FaceMatcher(listOf(BlockedPerson("X", listOf(floatArrayOf(1f, 0f)))))
        assertNull(matcher.check(listOf(floatArrayOf(1f, 0f, 0f))))
    }

    @Test
    fun pipelineOrderAndFaceSourceOnlyWhenNeeded() = runBlocking {
        var faceCalls = 0
        val source = FaceSource { faceCalls++; listOf(floatArrayOf(1f, 0f)) }
        val faces = FaceMatcher(listOf(BlockedPerson("X", listOf(floatArrayOf(1f, 0f)))))

        val byKeyword = FilterPipeline(KeywordFilter(listOf("開箱")), emptyMap(), faces, source).evaluate(video)
        assertIs<BlockReason.Keyword>((byKeyword as Decision.Block).reason)
        assertEquals(0, faceCalls)

        val byChannel = FilterPipeline(KeywordFilter(emptyList()), mapOf("UC1" to "3C"), faces, source).evaluate(video)
        assertIs<BlockReason.Channel>((byChannel as Decision.Block).reason)
        assertEquals(0, faceCalls)

        val byFace = FilterPipeline(KeywordFilter(emptyList()), emptyMap(), faces, source).evaluate(video)
        assertIs<BlockReason.Face>((byFace as Decision.Block).reason)
        assertEquals(1, faceCalls)
    }

    @Test
    fun faceSourceFailureAllows() = runBlocking {
        val faces = FaceMatcher(listOf(BlockedPerson("X", listOf(floatArrayOf(1f, 0f)))))
        val d = FilterPipeline(KeywordFilter(emptyList()), emptyMap(), faces) { error("network") }.evaluate(video)
        assertEquals(Decision.Allow, d)
    }
}
