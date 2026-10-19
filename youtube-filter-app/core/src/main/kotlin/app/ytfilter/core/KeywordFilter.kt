package app.ytfilter.core

import java.text.Normalizer

/**
 * Keyword rules. A rule is either a plain phrase (case/width-insensitive substring)
 * or a regex when written as `re:<pattern>`.
 */
class KeywordFilter(rules: List<String>) {
    private val matchers: List<Pair<String, (String) -> Boolean>> = rules
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .mapNotNull { rule ->
            if (rule.startsWith("re:")) {
                // A typo in one regex must not break the whole feed; such rules are skipped (see isValid).
                val regex = runCatching { Regex(rule.removePrefix("re:"), RegexOption.IGNORE_CASE) }.getOrNull()
                    ?: return@mapNotNull null
                rule to { text: String -> regex.containsMatchIn(text) }
            } else {
                val needle = normalize(rule)
                rule to { text: String -> normalize(text).contains(needle) }
            }
        }

    fun check(video: VideoItem): BlockReason.Keyword? {
        val fields = listOf(
            "title" to video.title,
            "channel" to video.channelTitle,
            "tags" to video.tags.joinToString(" "),
            "description" to video.description,
        )
        for ((rule, matches) in matchers) {
            for ((field, text) in fields) {
                if (text.isNotEmpty() && matches(text)) return BlockReason.Keyword(rule, field)
            }
        }
        return null
    }

    companion object {
        fun isValid(rule: String): Boolean =
            !rule.startsWith("re:") || runCatching { Regex(rule.removePrefix("re:")) }.isSuccess

        /** NFKC folds full-width characters (ＡＢＣ → ABC), then lowercase. */
        fun normalize(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFKC).lowercase()
    }
}
