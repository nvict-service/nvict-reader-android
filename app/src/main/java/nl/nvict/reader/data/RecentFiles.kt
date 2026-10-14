package nl.nvict.reader.data

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "reader")

data class RecentFile(val uri: Uri, val name: String, val lastPage: Int, val openedAt: Long)

/** Recente bestanden en instellingen, bewaard in DataStore. */
class RecentFiles(private val context: Context) {

    private val recentKey = stringPreferencesKey("recent")
    private val darkPagesKey = booleanPreferencesKey("dark_pages")

    val recent: Flow<List<RecentFile>> = context.dataStore.data.map { prefs -> parse(prefs[recentKey]) }
    val darkPages: Flow<Boolean> = context.dataStore.data.map { it[darkPagesKey] ?: false }

    suspend fun setDarkPages(on: Boolean) {
        context.dataStore.edit { it[darkPagesKey] = on }
    }

    suspend fun touch(uri: Uri, name: String, lastPage: Int = 0) {
        context.dataStore.edit { prefs ->
            val list = parse(prefs[recentKey]).filterNot { it.uri == uri }.toMutableList()
            list.add(0, RecentFile(uri, name, lastPage, System.currentTimeMillis()))
            prefs[recentKey] = serialize(list.take(MAX))
        }
    }

    suspend fun updatePage(uri: Uri, page: Int) {
        context.dataStore.edit { prefs ->
            val list = parse(prefs[recentKey]).map { if (it.uri == uri) it.copy(lastPage = page) else it }
            prefs[recentKey] = serialize(list)
        }
    }

    suspend fun lastPage(uri: Uri): Int = recent.first().firstOrNull { it.uri == uri }?.lastPage ?: 0

    suspend fun remove(uri: Uri) {
        context.dataStore.edit { prefs ->
            prefs[recentKey] = serialize(parse(prefs[recentKey]).filterNot { it.uri == uri })
        }
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    suspend fun clear() {
        context.dataStore.edit { it.remove(recentKey) }
    }

    private fun parse(json: String?): List<RecentFile> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                RecentFile(
                    Uri.parse(o.getString("uri")),
                    o.getString("name"),
                    o.optInt("page", 0),
                    o.optLong("at", 0L),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun serialize(list: List<RecentFile>): String {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("uri", it.uri.toString())
                    .put("name", it.name)
                    .put("page", it.lastPage)
                    .put("at", it.openedAt),
            )
        }
        return arr.toString()
    }

    companion object {
        private const val MAX = 20
    }
}
