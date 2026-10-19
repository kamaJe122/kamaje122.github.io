package app.ytfilter.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.ytfilter.core.BlockedPerson
import app.ytfilter.core.FaceMatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class StoredPerson(val name: String, val references: List<List<Float>>) {
    fun toBlockedPerson() = BlockedPerson(name, references.map { it.toFloatArray() })
}

@Serializable
data class Rules(
    val keywords: List<String> = emptyList(),
    val channels: Map<String, String> = emptyMap(), // channelId -> name
    val people: List<StoredPerson> = emptyList(),
    val faceThreshold: Float = FaceMatcher.DEFAULT_THRESHOLD,
)

private val Context.dataStore by preferencesDataStore("rules")

/** All filter rules live on-device only; nothing here is synced or uploaded. */
class RulesStore(private val context: Context) {
    private val key = stringPreferencesKey("rules_json")
    private val json = Json { ignoreUnknownKeys = true }

    val rules: Flow<Rules> = context.dataStore.data.map { prefs -> decode(prefs[key]) }

    suspend fun current(): Rules = rules.first()

    suspend fun update(transform: (Rules) -> Rules) {
        context.dataStore.edit { prefs ->
            prefs[key] = json.encodeToString(transform(decode(prefs[key])))
        }
    }

    private fun decode(raw: String?): Rules =
        raw?.let { runCatching { json.decodeFromString<Rules>(it) }.getOrNull() } ?: Rules()
}
